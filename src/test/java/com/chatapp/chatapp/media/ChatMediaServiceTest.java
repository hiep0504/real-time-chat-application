
package com.chatapp.chatapp.media;

import com.chatapp.chatapp.common.service.CloudinaryService;
import com.chatapp.chatapp.conversation.Conversation;
import com.chatapp.chatapp.conversation.ConversationRepository;
import com.chatapp.chatapp.message.Message;
import com.chatapp.chatapp.message.MessageRepository;
import com.chatapp.chatapp.user.User;
import com.chatapp.chatapp.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatMediaServiceTest {

    private CloudinaryService cloudinaryService;
    private ConversationRepository conversationRepository;
    private MessageRepository messageRepository;
    private UserRepository userRepository;
    private SimpMessagingTemplate messagingTemplate;

    private ChatMediaService chatMediaService;

    private User currentUser;
    private Conversation conversation;

    private final String email = "test@example.com";
    private final String userId = "user123";
    private final String conversationId = "conversation123";

    private final String imageUrl =
            "https://res.cloudinary.com/demo/image/upload/v123/"
                    + "chatapp/messages/images/test-image.png";

    @BeforeEach
    void setUp() {

        cloudinaryService = mock(CloudinaryService.class);
        conversationRepository = mock(ConversationRepository.class);
        messageRepository = mock(MessageRepository.class);
        userRepository = mock(UserRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);

        chatMediaService = new ChatMediaService(
                cloudinaryService,
                conversationRepository,
                messageRepository,
                userRepository,
                messagingTemplate
        );

        currentUser = mock(User.class);
        conversation = mock(Conversation.class);

        when(currentUser.getId()).thenReturn(userId);
        when(conversation.getId()).thenReturn(conversationId);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(currentUser));

        when(conversationRepository
                .findByIdAndMemberIdsContainingAndDeletedFalse(
                        conversationId,
                        userId
                ))
                .thenReturn(Optional.of(conversation));
    }

    private MockMultipartFile createTestImage() {
        return new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                new byte[]{
                        (byte) 0x89, 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A
                }
        );
    }

    @Test
    void sendImage_success_shouldSaveAndBroadcast()
            throws IOException {

        MockMultipartFile file = createTestImage();

        when(cloudinaryService.uploadChatImage(file))
                .thenReturn(imageUrl);

        when(messageRepository.save(any(Message.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        Message result = chatMediaService.sendImage(
                email,
                conversationId,
                file
        );

        assertNotNull(result);
        assertEquals("IMAGE", result.getType());
        assertEquals(imageUrl, result.getContent());

        verify(messageRepository).save(any(Message.class));

        verify(conversationRepository)
                .save(conversation);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/conversations/" + conversationId),
                any(Message.class)
        );

        verify(cloudinaryService, never())
                .deleteChatImage(anyString());
    }

    @Test
    void sendImage_mongoDbFails_shouldDeleteCloudinaryImage()
            throws IOException {

        MockMultipartFile file = createTestImage();

        when(cloudinaryService.uploadChatImage(file))
                .thenReturn(imageUrl);

        when(messageRepository.save(any(Message.class)))
                .thenThrow(
                        new RuntimeException("MongoDB save failed")
                );

        when(cloudinaryService.deleteChatImage(imageUrl))
                .thenReturn(true);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> chatMediaService.sendImage(
                        email,
                        conversationId,
                        file
                )
        );

        assertEquals(
                "MongoDB save failed",
                exception.getMessage()
        );

        verify(cloudinaryService, times(1))
                .deleteChatImage(imageUrl);

        verify(conversationRepository, never())
                .save(any(Conversation.class));

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void sendImage_cleanupFails_shouldPreserveOriginalError()
            throws IOException {

        MockMultipartFile file = createTestImage();

        when(cloudinaryService.uploadChatImage(file))
                .thenReturn(imageUrl);

        when(messageRepository.save(any(Message.class)))
                .thenThrow(
                        new RuntimeException("MongoDB save failed")
                );

        when(cloudinaryService.deleteChatImage(imageUrl))
                .thenThrow(
                        new IOException("Cloudinary delete failed")
                );

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> chatMediaService.sendImage(
                        email,
                        conversationId,
                        file
                )
        );

        assertEquals(
                "MongoDB save failed",
                exception.getMessage()
        );

        assertEquals(1, exception.getSuppressed().length);

        assertInstanceOf(
                IOException.class,
                exception.getSuppressed()[0]
        );

        verify(cloudinaryService)
                .deleteChatImage(imageUrl);

        verifyNoInteractions(messagingTemplate);
    }

    

}
