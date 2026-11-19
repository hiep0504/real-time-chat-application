package com.chatapp.chatapp.websocket;

import com.chatapp.chatapp.message.MessageService;
import com.chatapp.chatapp.message.dto.ChatMessageRequest;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWebSocketController {

    private final MessageService messageService;

    public ChatWebSocketController(
            MessageService messageService
    ) {
        this.messageService = messageService;
    }

    @MessageMapping("/chat.send")
    public void sendMessage(
            @Payload ChatMessageRequest request,
            Principal principal
    ) {

        // 1. Kiểm tra người dùng đã xác thực
        if (principal == null) {
            throw new SecurityException(
                    "Unauthenticated WebSocket user"
            );
        }

        // 2. Gửi, lưu MongoDB và broadcast WebSocket
        messageService.sendTextMessage(
                principal.getName(),
                request.conversationId(),
                request.content()
        );
    }
}
