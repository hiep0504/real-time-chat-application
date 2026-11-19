package com.chatapp.chatapp.conversation.dto;

import java.util.List;

public record CreateGroupConversationRequest(
        String name,
        List<String> memberIds
) {}
