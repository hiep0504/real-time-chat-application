package com.chatapp.chatapp.auth;

import com.chatapp.chatapp.auth.dto.SendOtpRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.chatapp.chatapp.auth.dto.VerifyOtpRequest;

import com.chatapp.chatapp.auth.dto.RegisterRequest;

import com.chatapp.chatapp.auth.dto.LoginRequest;
import com.chatapp.chatapp.auth.dto.AuthResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/send-otp")
    public ResponseEntity<String> sendOtp(
            @RequestBody SendOtpRequest request) {

        try {
            authService.sendEmailOtp(request.email());

            return ResponseEntity.ok(
                    "OTP has been sent to your email"
            );

        } catch (IllegalStateException exception) {
            return ResponseEntity.badRequest().body(
                    exception.getMessage()
            );
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(
            @RequestBody VerifyOtpRequest request) {

        try {
            authService.verifyEmailOtp(
                    request.email(),
                    request.otp()
            );

            return ResponseEntity.ok(
                    "Email verified successfully!"
            );

        } catch (IllegalStateException exception) {
            return ResponseEntity.badRequest().body(
                    exception.getMessage()
            );
        }
    }
    
    @PostMapping("/register")
    public ResponseEntity<String> register(
            @RequestBody RegisterRequest request) {

        try {
            authService.register(request);

            return ResponseEntity.ok(
                    "Account registered successfully!"
            );

        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(
                    exception.getMessage()
            );
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request) {

        try {
            AuthResponse response = authService.login(request);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(
                    exception.getMessage()
            );

        } catch (IllegalStateException exception) {
            return ResponseEntity.status(401).body(
                    exception.getMessage()
            );
        }
    }


}

