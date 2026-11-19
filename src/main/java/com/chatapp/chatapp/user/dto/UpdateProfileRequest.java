package com.chatapp.chatapp.user.dto;

public record UpdateProfileRequest(
        String username,
        String phoneNumber,
        String avatar
) {}
