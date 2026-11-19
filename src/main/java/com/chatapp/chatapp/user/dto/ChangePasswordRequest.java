package com.chatapp.chatapp.user.dto;

public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
) {}
