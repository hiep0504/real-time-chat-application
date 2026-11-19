
package com.chatapp.chatapp.user;

import com.chatapp.chatapp.user.dto.UpdateProfileRequest;
import com.chatapp.chatapp.user.dto.UserProfileResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.chatapp.chatapp.user.dto.ChangePasswordRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.chatapp.chatapp.common.service.CloudinaryService;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService cloudinaryService;

    public UserController(
                        UserRepository userRepository, 
                        PasswordEncoder passwordEncoder, 
                        CloudinaryService cloudinaryService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.cloudinaryService = cloudinaryService;
    }

    // Lấy thông tin người dùng hiện tại
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(
            Authentication authentication) {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException("User not found")
                );

        return ResponseEntity.ok(toProfileResponse(user));
    }

    // Cập nhật thông tin người dùng
    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(
            Authentication authentication,
            @RequestBody UpdateProfileRequest request) {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException("User not found")
                );

        // Kiểm tra username
        if (request.username() == null
                || request.username().isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Username cannot be empty");
        }

        String newUsername = request.username().trim();

        // Kiểm tra username đã được sử dụng chưa
        if (!newUsername.equals(user.getUsername())
                && userRepository.existsByUsername(newUsername)) {
            return ResponseEntity.badRequest()
                    .body("Username already exists");
        }

        // Cập nhật username
        user.setUsername(newUsername);

        // Chỉ cập nhật khi client gửi giá trị khác null
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber().trim());
        }

        if (request.avatar() != null) {
            user.setAvatar(request.avatar().trim());
        }

        // Lưu vào MongoDB
        User updatedUser = userRepository.save(user);

        return ResponseEntity.ok(toProfileResponse(updatedUser));
    }

    // Đổi mật khẩu
    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(
            Authentication authentication,
            @RequestBody ChangePasswordRequest request) {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException("User not found")
                );

        // Kiểm tra dữ liệu đầu vào
        if (request.currentPassword() == null
                || request.newPassword() == null
                || request.currentPassword().isBlank()
                || request.newPassword().isBlank()) {

            return ResponseEntity.badRequest()
                    .body("Password cannot be empty");
        }

        // Kiểm tra mật khẩu hiện tại
        if (!passwordEncoder.matches(
                request.currentPassword(),
                user.getPassword())) {

            return ResponseEntity.badRequest()
                    .body("Current password is incorrect");
        }

        // Kiểm tra mật khẩu mới
        if (request.newPassword().length() < 8) {
            return ResponseEntity.badRequest()
                    .body("New password must contain at least 8 characters");
        }

        // Không cho phép dùng lại mật khẩu cũ
        if (passwordEncoder.matches(
                request.newPassword(),
                user.getPassword())) {

            return ResponseEntity.badRequest()
                    .body("New password must be different from current password");
        }

        // Mã hóa mật khẩu mới
        user.setPassword(
                passwordEncoder.encode(request.newPassword())
        );

        // Lưu vào MongoDB
        userRepository.save(user);

        return ResponseEntity.ok(
                "Password changed successfully"
        );
    }

    
    @PostMapping(
            value = "/avatar",
            consumes = "multipart/form-data"
    )
    public ResponseEntity<?> uploadAvatar(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException("User not found")
                );

        // Lưu URL avatar cũ
        String oldAvatarUrl = user.getAvatar();

        String newAvatarUrl;

        try {
            // 1. Upload avatar mới
            newAvatarUrl = cloudinaryService.uploadImage(file);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (IOException e) {
            return ResponseEntity.status(502)
                    .body("Failed to upload avatar");
        }

        try {
            // 2. Lưu avatar mới vào MongoDB
            user.setAvatar(newAvatarUrl);
            userRepository.save(user);

        } catch (Exception e) {

            // Nếu MongoDB lưu thất bại, cố gắng xóa ảnh mới
            try {
                cloudinaryService.deleteImage(newAvatarUrl);
            } catch (Exception cleanupError) {
                System.err.println(
                        "Failed to clean up new avatar: "
                        + cleanupError.getMessage()
                );
            }

            return ResponseEntity.status(500)
                    .body("Failed to update avatar");
        }

        // 3. Xóa avatar cũ sau khi lưu thành công
        if (oldAvatarUrl != null
                && !oldAvatarUrl.equals(newAvatarUrl)) {

            try {
                boolean deleted = cloudinaryService.deleteImage(oldAvatarUrl);

                if (!deleted) {
                    System.out.println(
                            "Old avatar was not deleted or is not managed by Cloudinary"
                    );
                }

            } catch (Exception e) {
                // Không làm thất bại request vì avatar mới đã được lưu
                System.err.println(
                        "Failed to delete old avatar: "
                        + e.getMessage()
                );
            }
        }

        UserProfileResponse response = new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getAvatar(),
                user.getStatus()
        );

        return ResponseEntity.ok(response);
    }

    // Chuyển User thành DTO
    private UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getAvatar(),
                user.getStatus()
        );
    }
}
