package com.chatapp.chatapp.message;

import com.chatapp.chatapp.conversation.Conversation;
import com.chatapp.chatapp.conversation.ConversationRepository;
import com.chatapp.chatapp.user.User;
import com.chatapp.chatapp.user.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Map;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public Message sendTextMessage(
            String currentUserEmail,
            String conversationId,
            String content
    ) {

        // 1. Lấy người đang đăng nhập
        User currentUser = userRepository
                .findByEmail(currentUserEmail)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Current user not found"
                        )
                );

        // 2. Kiểm tra nội dung tin nhắn
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "Message content cannot be empty"
            );
        }

        String normalizedContent = content.trim();

        if (normalizedContent.length() > 5000) {
            throw new IllegalArgumentException(
                    "Message content must not exceed 5000 characters"
            );
        }

        // 3. Tìm conversation chưa bị xóa
        // Đồng thời kiểm tra người gửi là thành viên
        Conversation conversation = conversationRepository
                .findByIdAndMemberIdsContainingAndDeletedFalse(
                        conversationId,
                        currentUser.getId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Conversation not found or access denied"
                        )
                );

        // 4. Tạo tin nhắn
        Message message = new Message(
                conversation.getId(),
                currentUser.getId(),
                "TEXT",
                normalizedContent,
                null,
                null
        );

        
        // 5. Lưu tin nhắn vào MongoDB
        Message savedMessage = messageRepository.save(message);

        // 6. Cập nhật thời gian conversation
        conversation.setUpdatedAt(Instant.now());

        conversationRepository.save(conversation);

        // 7. Broadcast tin nhắn qua WebSocket
        messagingTemplate.convertAndSend(
                "/topic/conversations/" + savedMessage.getConversationId(),
                savedMessage
        );

        // 8. Trả tin nhắn vừa tạo
        return savedMessage;

    }

    public Page<Message> getMessages(
            String currentUserEmail,
            String conversationId,
            int page,
            int size
    ) {

        // 1. Tìm người đang đăng nhập
        User currentUser = userRepository
                .findByEmail(currentUserEmail)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Current user not found"
                        )
                );

        // 2. Kiểm tra tham số phân trang
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page must be greater than or equal to 0"
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Size must be between 1 and 100"
            );
        }

        // 3. Kiểm tra quyền truy cập conversation
        conversationRepository
                .findByIdAndMemberIdsContainingAndDeletedFalse(
                        conversationId,
                        currentUser.getId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Conversation not found or access denied"
                        )
                );

        // 4. Tạo Pageable
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        // 5. Lấy lịch sử tin nhắn từ MongoDB
        return messageRepository
                .findByConversationIdAndDeletedFalse(
                        conversationId,
                        pageable
                );
    }

    public Message updateMessage(
            String currentUserEmail,
            String messageId,
            String newContent
    ) {

        // 1. Lấy người đang đăng nhập
        User currentUser = userRepository
                .findByEmail(currentUserEmail)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Current user not found"
                        )
                );

        // 2. Kiểm tra nội dung mới
        if (newContent == null || newContent.isBlank()) {
            throw new IllegalArgumentException(
                    "Message content cannot be empty"
            );
        }

        String normalizedContent = newContent.trim();

        if (normalizedContent.length() > 5000) {
            throw new IllegalArgumentException(
                    "Message content must not exceed 5000 characters"
            );
        }

        // 3. Tìm tin nhắn
        Message message = messageRepository
                .findById(messageId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Message not found"
                        )
                );

        // 4. Kiểm tra người gửi
        if (!currentUser.getId().equals(message.getSenderId())) {
            throw new SecurityException(
                    "You can only edit your own messages"
            );
        }

        // 5. Kiểm tra tin nhắn đã xóa chưa
        if (message.isDeleted()) {
            throw new IllegalArgumentException(
                    "Cannot edit a deleted message"
            );
        }

        // 6. Chỉ cho phép sửa tin nhắn TEXT
        if (!"TEXT".equals(message.getType())) {
            throw new IllegalArgumentException(
                    "Only text messages can be edited"
            );
        }

        // 7. Kiểm tra conversation còn tồn tại
        // và người gửi vẫn là thành viên
        conversationRepository
                .findByIdAndMemberIdsContainingAndDeletedFalse(
                        message.getConversationId(),
                        currentUser.getId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Conversation not found or access denied"
                        )
                );

        // 8. Cập nhật nội dung
        message.setContent(normalizedContent);
        message.setUpdatedAt(Instant.now());

        // 9. Lưu MongoDB
        Message updatedMessage = messageRepository.save(message);

        // 10. Tạo sự kiện cập nhật
        Map<String, Object> event = Map.of(
                "event", "MESSAGE_UPDATED",
                "messageId", updatedMessage.getId(),
                "conversationId", updatedMessage.getConversationId(),
                "content", updatedMessage.getContent(),
                "updatedAt", updatedMessage.getUpdatedAt().toString()
        );

        // 11. Broadcast qua WebSocket
        messagingTemplate.convertAndSend(
        "/topic/conversations/" + updatedMessage.getConversationId(),
        (Object) event
        );

        return updatedMessage;
    }

    public void deleteMessage(
            String currentUserEmail,
            String messageId
    ) {

        // 1. Lấy người đang đăng nhập
        User currentUser = userRepository
                .findByEmail(currentUserEmail)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Current user not found"
                        )
                );

        // 2. Tìm tin nhắn
        Message message = messageRepository
                .findById(messageId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Message not found"
                        )
                );

        // 3. Kiểm tra quyền sở hữu
        if (!currentUser.getId().equals(message.getSenderId())) {
            throw new SecurityException(
                    "You can only delete your own messages"
            );
        }

        // 4. Kiểm tra tin nhắn đã xóa chưa
        if (message.isDeleted()) {
            throw new IllegalArgumentException(
                    "Message already deleted"
            );
        }

        // 5. Kiểm tra conversation và quyền thành viên
        conversationRepository
                .findByIdAndMemberIdsContainingAndDeletedFalse(
                        message.getConversationId(),
                        currentUser.getId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Conversation not found or access denied"
                        )
                );

        // 6. Soft delete
        message.setDeleted(true);
        message.setUpdatedAt(Instant.now());

        // 7. Lưu thay đổi
        Message deletedMessage = messageRepository.save(message);

        // 8. Tạo sự kiện xóa
        Map<String, Object> event = Map.of(
                "event", "MESSAGE_DELETED",
                "messageId", deletedMessage.getId(),
                "conversationId", deletedMessage.getConversationId()
        );

        // 9. Broadcast qua WebSocket
        messagingTemplate.convertAndSend(
                "/topic/conversations/" + deletedMessage.getConversationId(),
                (Object)event
        );
    }


}
