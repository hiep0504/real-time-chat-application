
package com.chatapp.chatapp.user.dto;

public record UserProfileResponse(
        String id,
        String username,
        String email,
        String phoneNumber,
        String avatar,
        String status
) {}
