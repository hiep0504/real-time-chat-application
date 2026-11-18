
package com.chatapp.chatapp.auth;

import com.chatapp.chatapp.user.UserRepository;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

import com.chatapp.chatapp.auth.dto.RegisterRequest;
import com.chatapp.chatapp.user.User;

import com.chatapp.chatapp.auth.dto.LoginRequest;
import com.chatapp.chatapp.auth.dto.AuthResponse;

import com.chatapp.chatapp.security.JwtService;

import java.util.Locale;

@Service
public class AuthService {

    private final OtpRepository otpRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final JwtService jwtService;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            OtpRepository otpRepository,
            UserRepository userRepository,
            EmailService emailService,
            JwtService jwtService ) {

        this.otpRepository = otpRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.jwtService = jwtService;
    }

    public void sendEmailOtp(String email) {

        // 1. Chuẩn hóa email
        email = email.trim().toLowerCase(Locale.ROOT);

        // 2. Kiểm tra email đã đăng ký chưa
        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException(
                    "Email is already registered"
            );
        }

        // 3. Kiểm tra thời gian gửi lại OTP
        var existingOtp = otpRepository.findByEmail(email);

        if (existingOtp.isPresent()) {
            OtpVerification oldOtp = existingOtp.get();

            if (oldOtp.getCreatedAt() != null &&
                    oldOtp.getCreatedAt().plusSeconds(60)
                            .isAfter(LocalDateTime.now())) {

                throw new IllegalStateException(
                        "Please wait before requesting another OTP"
                );
            }

            otpRepository.deleteByEmail(email);
        }

        // 4. Tạo OTP ngẫu nhiên gồm 6 chữ số
        String otp = String.format(
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        // 5. Tạo thông tin xác minh OTP
        OtpVerification verification = new OtpVerification();

        verification.setEmail(email);
        verification.setOtpHash(passwordEncoder.encode(otp));
        verification.setCreatedAt(LocalDateTime.now());
        verification.setExpiresAt(
                LocalDateTime.now().plusMinutes(5)
        );
        verification.setAttempts(0);
        verification.setVerified(false);

        // 6. Lưu OTP đã mã hóa vào MongoDB
        otpRepository.save(verification);

        // 7. Gửi OTP qua email
        try {
            emailService.sendOtpEmail(email, otp);
        } catch (RuntimeException exception) {
            otpRepository.deleteByEmail(email);
            throw exception;
        }
    }

    public void verifyEmailOtp(String email, String otp) {

        // 1. Chuẩn hóa email
        email = email.trim().toLowerCase(Locale.ROOT);

        // 2. Tìm OTP theo email
        OtpVerification verification = otpRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "OTP not found. Please request a new OTP."
                ));

        // 3. Kiểm tra đã xác minh chưa
        if (verification.isVerified()) {
            throw new IllegalStateException(
                    "Email has already been verified."
            );
        }

        // 4. Kiểm tra số lần nhập sai
        if (verification.getAttempts() >= 5) {
            otpRepository.deleteByEmail(email);

            throw new IllegalStateException(
                    "Too many incorrect attempts. Please request a new OTP."
            );
        }

        // 5. Kiểm tra OTP hết hạn
        if (verification.getExpiresAt() == null ||
                verification.getExpiresAt().isBefore(LocalDateTime.now())) {

            otpRepository.deleteByEmail(email);

            throw new IllegalStateException(
                    "OTP has expired. Please request a new OTP."
            );
        }

        // 6. Kiểm tra OTP
        if (verification.getOtpHash() == null ||
                !passwordEncoder.matches(otp, verification.getOtpHash())) {

            verification.setAttempts(verification.getAttempts() + 1);
            otpRepository.save(verification);

            throw new IllegalStateException(
                    "Invalid OTP. Attempts: "
                            + verification.getAttempts() + "/5"
            );
        }

        // 7. Xác minh thành công
        verification.setVerified(true);
        verification.setOtpHash(null);

        otpRepository.save(verification);
    }

    public void register(RegisterRequest request) {

        // 1. Kiểm tra dữ liệu đầu vào
        if (request.username() == null || request.username().isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }

        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        if (request.password() == null || request.password().length() < 8) {
            throw new IllegalArgumentException(
                    "Password must contain at least 8 characters"
            );
        }

        // 2. Chuẩn hóa dữ liệu
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username().trim();

        // 3. Kiểm tra email đã đăng ký chưa
        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("Email is already registered");
        }

        // 4. Kiểm tra username đã tồn tại chưa
        if (userRepository.existsByUsername(username)) {
            throw new IllegalStateException("Username is already taken");
        }

        // 5. Kiểm tra email đã xác minh OTP chưa
        OtpVerification verification = otpRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Please verify your email first"
                ));

        if (!verification.isVerified()) {
            throw new IllegalStateException("Email has not been verified");
        }

        // 6. Kiểm tra OTP còn hạn không
        if (verification.getExpiresAt() == null ||
                !verification.getExpiresAt().isAfter(LocalDateTime.now())) {

            otpRepository.deleteByEmail(email);

            throw new IllegalStateException(
                    "Verification has expired. Please verify your email again"
            );
        }

        // 7. Tạo User mới
        User user = new User();

        user.setUsername(username);
        user.setEmail(email);

        // Không lưu mật khẩu dạng chữ thường
        user.setPassword(passwordEncoder.encode(request.password()));

        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());

        // 8. Lưu User vào MongoDB
        userRepository.save(user);

        // 9. Xóa thông tin OTP sau khi đăng ký thành công
        otpRepository.deleteByEmail(email);
    }

    public AuthResponse login(LoginRequest request) {

        // 1. Kiểm tra dữ liệu đầu vào
        if (request.email() == null || request.email().isBlank()
                || request.password() == null || request.password().isBlank()) {

            throw new IllegalArgumentException(
                    "Email and password are required"
            );
        }

        // 2. Chuẩn hóa email
        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        // 3. Tìm User theo email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Invalid email or password"
                ));

        // 4. Kiểm tra mật khẩu bằng BCrypt
        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new IllegalStateException(
                    "Invalid email or password"
            );
        }

        String token = jwtService.generateToken(user.getEmail());
        // 5. Trả thông tin User
        return new AuthResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatar(),
                token,
                "Login successful"
        );
    }



}
