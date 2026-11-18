package com.chatapp.chatapp.auth.dto;

public record VerifyOtpRequest(
        String email,
        String otp
) {
}
