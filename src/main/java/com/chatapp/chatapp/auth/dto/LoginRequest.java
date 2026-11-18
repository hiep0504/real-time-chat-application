package com.chatapp.chatapp.auth.dto;

public record LoginRequest(
        String email,
        String password
) {
}
