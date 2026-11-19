package com.chatapp.chatapp.common.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Service
public class CloudinaryService {
     private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String uploadImage(MultipartFile file) throws IOException {

        // 1. Kiểm tra file rỗng
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        // 2. Kiểm tra dung lượng (tối đa 5 MB)
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("Image size must not exceed 5MB");
        }

        // 3. Kiểm tra Content-Type
        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Only JPG, PNG and WEBP images are allowed"
            );
        }

        // long startTime = System.currentTimeMillis();
        // 4. Upload lên Cloudinary
        Map<?, ?> uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "folder", "chatapp/avatars",
                        "public_id", UUID.randomUUID().toString(),
                        "resource_type", "image"
                )
        );
        // // Kết thúc đo thời gian
        // long endTime = System.currentTimeMillis();

        // // In thời gian upload ra Terminal
        // System.out.println(
        //     "Cloudinary upload time: " + (endTime - startTime) + " ms"
        // );

        // 5. Lấy URL ảnh
        Object secureUrl = uploadResult.get("secure_url");

        if (secureUrl == null) {
            throw new IOException("Cloudinary did not return an image URL");
        }

        return secureUrl.toString();
    }

    private String extractPublicId(String imageUrl) {

        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(imageUrl);

            // Chỉ xử lý URL HTTPS của Cloudinary
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"res.cloudinary.com".equalsIgnoreCase(uri.getHost())) {
                return null;
            }

            String path = uri.getPath();
            String prefix = "/" + cloudinary.config.cloudName
                    + "/image/upload/";

            if (!path.startsWith(prefix)) {
                return null;
            }

            String remaining = path.substring(prefix.length());

            // Bỏ version, ví dụ: v1795101561/
            remaining = remaining.replaceFirst("^v\\d+/", "");

            // Chỉ cho phép xóa avatar trong thư mục của ứng dụng
            if (!remaining.startsWith("chatapp/avatars/")) {
                return null;
            }

            int extensionIndex = remaining.lastIndexOf(".");

            if (extensionIndex == -1) {
                return null;
            }

            String publicId = remaining.substring(0, extensionIndex);

            return URLDecoder.decode(
                    publicId,
                    StandardCharsets.UTF_8
            );

        } catch (Exception e) {
            return null;
        }
    }

    public boolean deleteImage(String imageUrl) throws IOException {

        String publicId = extractPublicId(imageUrl);

        if (publicId == null) {
            return false;
        }

        Map<?, ?> result = cloudinary.uploader().destroy(
                publicId,
                ObjectUtils.asMap(
                        "resource_type", "image",
                        "invalidate", true
                )
        );

        return "ok".equals(result.get("result"));
    }
}
