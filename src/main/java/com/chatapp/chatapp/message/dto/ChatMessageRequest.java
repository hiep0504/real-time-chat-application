package com.chatapp.chatapp.message.dto;

public record ChatMessageRequest(
        String conversationId,
        String content
) {
}
