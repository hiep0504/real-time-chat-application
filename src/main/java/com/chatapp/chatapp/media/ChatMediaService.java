package com.chatapp.chatapp.media;

import com.chatapp.chatapp.common.service.CloudinaryService;
import com.chatapp.chatapp.conversation.Conversation;
import com.chatapp.chatapp.conversation.ConversationRepository;
import com.chatapp.chatapp.message.Message;
import com.chatapp.chatapp.message.MessageRepository;
import com.chatapp.chatapp.user.User;
import com.chatapp.chatapp.user.UserRepository;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;

@Service
public class ChatMediaService {

    private final CloudinaryService cloudinaryService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatMediaService(
            CloudinaryService cloudinaryService,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.cloudinaryService = cloudinaryService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public Message sendImage(
            String currentUserEmail,
            String conversationId,
            MultipartFile file
    ) throws IOException {

        // 1. Kiểm tra người dùng đăng nhập
        User currentUser = userRepository
                .findByEmail(currentUserEmail)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Current user not found"
                        )
                );

        // 2. Kiểm tra quyền tham gia conversation
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

        
        // 3. Upload ảnh lên Cloudinary
        String imageUrl = cloudinaryService.uploadChatImage(file);

        Message savedMessage;

        try {
            // 4. Tạo tin nhắn IMAGE
            Message message = new Message(
                    conversation.getId(),
                    currentUser.getId(),
                    "IMAGE",
                    imageUrl,
                    file.getOriginalFilename(),
                    file.getSize()
            );

            // 5. Lưu tin nhắn vào MongoDB
            savedMessage = messageRepository.save(message);

        } catch (RuntimeException e) {

            // Nếu lưu MongoDB thất bại, xóa ảnh trên Cloudinary
            try {
                boolean deleted = cloudinaryService.deleteChatImage(imageUrl);

                if (!deleted) {
                    System.err.println(
                            "Cloudinary cleanup was not completed: " + imageUrl
                    );
                }

            } catch (IOException cleanupError) {
                System.err.println(
                        "Cloudinary cleanup failed: "
                                + cleanupError.getMessage()
                );
                e.addSuppressed(cleanupError);
            }

            // Ném lại lỗi ban đầu
            throw e;
        }

        // 6. Cập nhật thời gian conversation
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        // 7. Broadcast tin nhắn ảnh qua WebSocket
        messagingTemplate.convertAndSend(
                "/topic/conversations/" + savedMessage.getConversationId(),
                savedMessage
        );

        // 8. Trả về tin nhắn vừa tạo
        return savedMessage;
    }
    public Message sendFile(
            String currentUserEmail,
            String conversationId,
            MultipartFile file
    ) throws IOException {

        // 1. Kiểm tra người dùng đăng nhập
        User currentUser = userRepository
                .findByEmail(currentUserEmail)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Current user not found"
                        )
                );

        // 2. Kiểm tra quyền tham gia conversation
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

        // 3. Upload tài liệu lên Cloudinary
        String fileUrl = cloudinaryService.uploadChatFile(file);

        Message savedMessage;

        try {
            // 4. Tạo tin nhắn FILE
            Message message = new Message(
                    conversation.getId(),
                    currentUser.getId(),
                    "FILE",
                    fileUrl,
                    file.getOriginalFilename(),
                    file.getSize()
            );

            // 5. Lưu tin nhắn vào MongoDB
            savedMessage = messageRepository.save(message);

        } catch (RuntimeException e) {

            // Rollback tài liệu nếu MongoDB lưu thất bại
            try {
                boolean deleted =
                        cloudinaryService.deleteChatFile(fileUrl);

                if (!deleted) {
                    System.err.println(
                            "Cloudinary file cleanup was not completed: "
                                    + fileUrl
                    );
                }

            } catch (IOException cleanupError) {
                System.err.println(
                        "Cloudinary file cleanup failed: "
                                + cleanupError.getMessage()
                );

                e.addSuppressed(cleanupError);
            }

            throw e;
        }

        // 6. Cập nhật thời gian conversation
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        // 7. Broadcast tin nhắn FILE qua WebSocket
        messagingTemplate.convertAndSend(
                "/topic/conversations/"
                        + savedMessage.getConversationId(),
                (Object) savedMessage
        );

        // 8. Trả về tin nhắn vừa tạo
        return savedMessage;
    }
}
