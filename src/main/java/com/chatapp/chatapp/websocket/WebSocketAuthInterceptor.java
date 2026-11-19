package com.chatapp.chatapp.websocket;

import com.chatapp.chatapp.security.JwtService;
import com.chatapp.chatapp.conversation.ConversationRepository;
import com.chatapp.chatapp.user.User;
import com.chatapp.chatapp.user.UserRepository;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String CONVERSATION_TOPIC_PREFIX =
            "/topic/conversations/";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;

    public WebSocketAuthInterceptor(
            JwtService jwtService,
            UserRepository userRepository,
            ConversationRepository conversationRepository
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            authenticateConnect(accessor);
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscribe(accessor);
        }

        if (StompCommand.SEND.equals(command)) {
            authorizeSend(accessor);
        }

        return message;
    }

    private void authenticateConnect(
            StompHeaderAccessor accessor
    ) {

        String authorization =
                accessor.getFirstNativeHeader("Authorization");

        if (authorization == null ||
                !authorization.startsWith("Bearer ")) {
            throw new SecurityException(
                    "Missing WebSocket JWT token"
            );
        }

        String token = authorization.substring(7);

        // Đổi tên method cho phù hợp JwtService hiện tại.
        if (!jwtService.isTokenValid(token)) {
            throw new SecurityException(
                    "Invalid WebSocket JWT token"
            );
        }

        String email = jwtService.extractEmail(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new SecurityException("User not found")
                );

        Principal principal =
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        List.of()
                );

        accessor.setUser(principal);
    }

    private void authorizeSubscribe(
            StompHeaderAccessor accessor
    ) {

        User user = getAuthenticatedUser(accessor);

        String destination = accessor.getDestination();

        if (destination == null ||
                !destination.startsWith(
                        CONVERSATION_TOPIC_PREFIX
                )) {
            throw new SecurityException(
                    "Subscription destination not allowed"
            );
        }

        String conversationId = destination.substring(
                CONVERSATION_TOPIC_PREFIX.length()
        );

        if (conversationId.isBlank() ||
                conversationId.contains("/")) {
            throw new SecurityException(
                    "Invalid conversation destination"
            );
        }

        boolean allowed = conversationRepository
                .findByIdAndMemberIdsContainingAndDeletedFalse(
                        conversationId,
                        user.getId()
                )
                .isPresent();

        if (!allowed) {
            throw new SecurityException(
                    "Conversation access denied"
            );
        }
    }

    private void authorizeSend(
            StompHeaderAccessor accessor
    ) {

        getAuthenticatedUser(accessor);

        // Chưa triển khai gửi message ở bước này.
        // Chỉ cho phép destination dự kiến cho chat.
        String destination = accessor.getDestination();

        if (!"/app/chat.send".equals(destination)) {
            throw new SecurityException(
                    "Message destination not allowed"
            );
        }
    }

    private User getAuthenticatedUser(
            StompHeaderAccessor accessor
    ) {

        Principal principal = accessor.getUser();

        if (principal == null) {
            throw new SecurityException(
                    "Unauthenticated WebSocket session"
            );
        }

        return userRepository
                .findByEmail(principal.getName())
                .orElseThrow(() ->
                        new SecurityException("User not found")
                );
    }
}
