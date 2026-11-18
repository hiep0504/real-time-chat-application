package com.chatapp.chatapp.auth.dto;

public record RegisterRequest(
        String username,
        String email,
        String password
) {
}