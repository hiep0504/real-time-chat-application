
package com.chatapp.chatapp.auth.dto;

public record AuthResponse(
    String id,
    String username,
    String email,
    String avatar,
    String token,
    String message
) {}
