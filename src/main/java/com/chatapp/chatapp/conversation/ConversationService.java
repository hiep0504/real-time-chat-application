package com.chatapp.chatapp.conversation;

import com.chatapp.chatapp.user.User;
import com.chatapp.chatapp.user.UserRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            UserRepository userRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
    }

    // 1. Tạo cuộc trò chuyện cá nhân
    public Conversation createPrivateConversation(
            String currentUserEmail,
            String receiverId
    ) {

        // Tìm người đang đăng nhập
        User currentUser = userRepository
                .findByEmail(currentUserEmail)
                .orElseThrow(() ->
                        new IllegalStateException("Current user not found")
                );

        // Tìm người nhận
        if (receiverId == null || receiverId.isBlank()) {
            throw new IllegalArgumentException("Receiver ID is required");
        }

        User receiver = userRepository
                .findById(receiverId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Receiver not found")
                );

        // Không cho phép chat với chính mình
        if (currentUser.getId().equals(receiver.getId())) {
            throw new IllegalArgumentException(
                    "Cannot create a conversation with yourself"
            );
        }

        // Kiểm tra cuộc trò chuyện đã tồn tại chưa
        List<Conversation> existingConversations =
                conversationRepository
                        .findByTypeAndMemberIdsContainingAndDeletedFalse(
                                "PRIVATE",
                                currentUser.getId()
                        );

        for (Conversation conversation : existingConversations) {

            if (conversation.getMemberIds() != null
                    && conversation.getMemberIds().size() == 2
                    && conversation.getMemberIds().contains(receiver.getId())) {

                // Nếu đã tồn tại thì trả về cuộc trò chuyện cũ
                return conversation;
            }
        }

        // Tạo cuộc trò chuyện mới
        Conversation conversation = new Conversation(
                "PRIVATE",
                null,
                null,
                List.of(currentUser.getId(), receiver.getId()),
                currentUser.getId()
        );

        return conversationRepository.save(conversation);
    }

    // 2. Lấy danh sách cuộc trò chuyện của mình
    public List<Conversation> getMyConversations(String currentUserEmail) {

        User currentUser = userRepository
                .findByEmail(currentUserEmail)
                .orElseThrow(() ->
                        new IllegalStateException("Current user not found")
                );

        return conversationRepository
                .findByMemberIdsContainingAndDeletedFalseOrderByUpdatedAtDesc(
                        currentUser.getId()
                );
    }

    // 3. Xem chi tiết cuộc trò chuyện
    public Conversation getConversationById(
            String currentUserEmail,
            String conversationId
    ) {

        User currentUser = userRepository
                .findByEmail(currentUserEmail)
                .orElseThrow(() ->
                        new IllegalStateException("Current user not found")
                );

        return conversationRepository
                .findByIdAndMemberIdsContainingAndDeletedFalse(
                        conversationId,
                        currentUser.getId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Conversation not found or access denied"
                        )
                );
    }
    // 4. Tạo cuộc trò chuyện nhóm
    public Conversation createGroupConversation(
            String currentUserEmail,
            String groupName,
            List<String> memberIds
    ) {

        // 1. Tìm người đang đăng nhập
        User currentUser = userRepository
                .findByEmail(currentUserEmail)
                .orElseThrow(() ->
                        new IllegalStateException("Current user not found")
                );

        // 2. Kiểm tra tên nhóm
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException(
                    "Group name cannot be empty"
            );
        }

        String normalizedName = groupName.trim();

        if (normalizedName.length() > 100) {
            throw new IllegalArgumentException(
                    "Group name must not exceed 100 characters"
            );
        }

        // 3. Kiểm tra danh sách thành viên
        if (memberIds == null || memberIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Group members cannot be empty"
            );
        }

        // 4. Tạo danh sách ID không trùng lặp
        Set<String> uniqueMemberIds = new HashSet<>();

        // Tự động thêm người tạo nhóm
        uniqueMemberIds.add(currentUser.getId());

        for (String memberId : memberIds) {

            if (memberId == null || memberId.isBlank()) {
                throw new IllegalArgumentException(
                        "Member ID cannot be empty"
                );
            }

            if (!uniqueMemberIds.add(memberId)) {
                throw new IllegalArgumentException(
                        "Duplicate member ID: " + memberId
                );
            }
        }

        // 5. Nhóm phải có ít nhất 3 người
        if (uniqueMemberIds.size() < 3) {
            throw new IllegalArgumentException(
                    "Group must have at least 3 members"
            );
        }

        // 6. Kiểm tra các thành viên có tồn tại không
        long existingUserCount =
                userRepository.findAllById(uniqueMemberIds).size();

        if (existingUserCount != uniqueMemberIds.size()) {
            throw new IllegalArgumentException(
                    "One or more members do not exist"
            );
        }

        // 7. Tạo nhóm chat
        Conversation conversation = new Conversation(
                "GROUP",
                normalizedName,
                null,
                new ArrayList<>(uniqueMemberIds),
                currentUser.getId()
        );

        // 8. Lưu vào MongoDB
        return conversationRepository.save(conversation);
    }

    public Conversation addGroupMember(
            String currentUserEmail,
            String conversationId,
            String newMemberId
    ) {

        // 1. Tìm người đang đăng nhập
        User currentUser = userRepository
                .findByEmail(currentUserEmail)
                .orElseThrow(() ->
                        new IllegalStateException("Current user not found")
                );

        // 2. Tìm nhóm
        Conversation conversation = conversationRepository
                .findByIdAndMemberIdsContainingAndDeletedFalse(
                        conversationId,
                        currentUser.getId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Group not found or access denied"
                        )
                );

        // 3. Kiểm tra có phải nhóm chat không
        if (!"GROUP".equals(conversation.getType())) {
            throw new IllegalArgumentException(
                    "Cannot add members to a private conversation"
            );
        }

        // 4. Chỉ người tạo nhóm được thêm thành viên
        if (!currentUser.getId().equals(conversation.getCreatedBy())) {
            throw new IllegalArgumentException(
                    "Only group creator can add members"
            );
        }

        // 5. Kiểm tra ID người muốn thêm
        if (newMemberId == null || newMemberId.isBlank()) {
            throw new IllegalArgumentException(
                    "User ID cannot be empty"
            );
        }

        // 6. Kiểm tra User tồn tại
        User newMember = userRepository
                .findById(newMemberId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found")
                );

        // 7. Kiểm tra đã có trong nhóm chưa
        if (conversation.getMemberIds().contains(newMember.getId())) {
            throw new IllegalArgumentException(
                    "User is already a group member"
            );
        }

        // 8. Thêm thành viên
        conversation.getMemberIds().add(newMember.getId());

        // 9. Cập nhật thời gian
        conversation.setUpdatedAt(java.time.Instant.now());

        // 10. Lưu vào MongoDB
        return conversationRepository.save(conversation);
    }

    public Conversation removeGroupMember(
            String currentUserEmail,
            String conversationId,
            String memberId
    ) {

        // 1. Lấy người đang đăng nhập
        User currentUser = userRepository
                .findByEmail(currentUserEmail)
                .orElseThrow(() ->
                        new IllegalStateException("Current user not found")
                );

        // 2. Tìm nhóm mà người dùng đang tham gia
        Conversation conversation = conversationRepository
                .findByIdAndMemberIdsContainingAndDeletedFalse(
                        conversationId,
                        currentUser.getId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Group not found or access denied"
                        )
                );

        // 3. Kiểm tra loại conversation
        if (!"GROUP".equals(conversation.getType())) {
            throw new IllegalArgumentException(
                    "Cannot remove members from a private conversation"
            );
        }

        // 4. Chỉ người tạo nhóm được xóa thành viên
        if (!currentUser.getId().equals(conversation.getCreatedBy())) {
            throw new IllegalArgumentException(
                    "Only group creator can remove members"
            );
        }

        // 5. Kiểm tra memberId
        if (memberId == null || memberId.isBlank()) {
            throw new IllegalArgumentException(
                    "Member ID cannot be empty"
            );
        }

        // 6. Không cho người tạo nhóm tự xóa mình
        if (memberId.equals(conversation.getCreatedBy())) {
            throw new IllegalArgumentException(
                    "Group creator cannot remove themselves"
            );
        }

        // 7. Kiểm tra thành viên có trong nhóm không
        if (!conversation.getMemberIds().contains(memberId)) {
            throw new IllegalArgumentException(
                    "User is not a member of this group"
            );
        }

        // 8. Xóa ID thành viên khỏi danh sách
        conversation.getMemberIds().remove(memberId);

        // 9. Cập nhật thời gian
        conversation.setUpdatedAt(java.time.Instant.now());

        // 10. Lưu thay đổi vào MongoDB
        return conversationRepository.save(conversation);
    }

    public Conversation leaveGroup(
            String currentUserEmail,
            String conversationId
    ) {

        // 1. Tìm người đang đăng nhập
        User currentUser = userRepository
                .findByEmail(currentUserEmail)
                .orElseThrow(() ->
                        new IllegalStateException("Current user not found")
                );

        // 2. Tìm conversation mà user đang tham gia
        Conversation conversation = conversationRepository
                .findByIdAndMemberIdsContainingAndDeletedFalse(
                        conversationId,
                        currentUser.getId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Group not found or you are not a member"
                        )
                );

        // 3. Kiểm tra loại conversation
        if (!"GROUP".equals(conversation.getType())) {
            throw new IllegalArgumentException(
                    "Cannot leave a private conversation"
            );
        }

        // 4. Không cho người tạo nhóm rời nhóm
        if (currentUser.getId().equals(
                conversation.getCreatedBy()
        )) {
            throw new IllegalArgumentException(
                    "Group creator cannot leave the group"
            );
        }

        // 5. Xóa chính mình khỏi danh sách thành viên
        conversation.getMemberIds().remove(
                currentUser.getId()
        );

        // 6. Cập nhật thời gian
        conversation.setUpdatedAt(
                java.time.Instant.now()
        );

        // 7. Lưu vào MongoDB
        return conversationRepository.save(conversation);
    }

    public void deleteGroup(
            String currentUserEmail,
            String conversationId
    ) {

        // 1. Lấy người đang đăng nhập
        User currentUser = userRepository
                .findByEmail(currentUserEmail)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Current user not found"
                        )
                );

        // 2. Tìm nhóm chưa bị xóa
        Conversation conversation = conversationRepository
                .findByIdAndDeletedFalse(conversationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Conversation not found"
                        )
                );

        // 3. Kiểm tra loại cuộc trò chuyện
        if (!"GROUP".equals(conversation.getType())) {
            throw new IllegalArgumentException(
                    "Only group conversations can be deleted"
            );
        }

        // 4. Kiểm tra quyền
        if (!currentUser.getId().equals(
                conversation.getCreatedBy()
        )) {
            throw new SecurityException(
                    "Only group creator can delete this group"
            );
        }

        // 5. Đánh dấu nhóm đã xóa
        conversation.setDeleted(true);

        // 6. Cập nhật thời gian
        conversation.setUpdatedAt(
                java.time.Instant.now()
        );

        // 7. Lưu thay đổi vào MongoDB
        conversationRepository.save(conversation);
    }

}
