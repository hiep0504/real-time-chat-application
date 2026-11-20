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
import java.util.Arrays;
import java.util.Locale;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

@Service
public class CloudinaryService {
     private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
     private static final long MAX_CHAT_FILE_SIZE = 20L * 1024 * 1024;

    private static final Set<String> ALLOWED_CHAT_EXTENSIONS = Set.of(
        "txt", "pdf", "doc", "docx",
        "xls", "xlsx", "csv",
        "ppt", "pptx",
        "zip", "rar", "7z",
        "java", "js", "json", "html", "css", "py", "xml"
    );

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
        return uploadImageToFolder(file, "chatapp/avatars");
    }

    public String uploadChatImage(MultipartFile file) throws IOException {
        return uploadImageToFolder(file, "chatapp/messages/images");
    }

    private String uploadImageToFolder(
            MultipartFile file,
            String folder
    ) throws IOException {

        // 1. Kiểm tra file rỗng
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "File cannot be empty"
            );
        }

        // 2. Kiểm tra dung lượng
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Image size must not exceed 5MB"
            );
        }

        // 3. Kiểm tra Content-Type
        String contentType = file.getContentType();

        if (contentType == null ||
                !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Only JPG, PNG and WEBP images are allowed"
            );
        }

        // 4. Đọc nội dung file một lần
        byte[] imageBytes = file.getBytes();

        // 5. Kiểm tra chữ ký ảnh
        validateImageContent(imageBytes, contentType);

        // 6. Upload lên Cloudinary
        Map<?, ?> uploadResult = cloudinary.uploader().upload(
                imageBytes,
                ObjectUtils.asMap(
                        "folder", folder,
                        "public_id", UUID.randomUUID().toString(),
                        "resource_type", "image"
                )
        );

        // 5. Lấy URL ảnh
        Object secureUrl = uploadResult.get("secure_url");

        if (secureUrl == null) {
            throw new IOException(
                    "Cloudinary did not return an image URL"
            );
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

    private void validateImageContent(
            byte[] bytes,
            String contentType
    ) {

        boolean valid = false;

        switch (contentType) {

            case "image/jpeg" -> {
                valid = bytes.length >= 3
                        && (bytes[0] & 0xFF) == 0xFF
                        && (bytes[1] & 0xFF) == 0xD8
                        && (bytes[2] & 0xFF) == 0xFF;
            }

            case "image/png" -> {
                byte[] pngSignature = {
                        (byte) 0x89, 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A
                };

                valid = bytes.length >= pngSignature.length
                        && Arrays.equals(
                                Arrays.copyOf(bytes, 8),
                                pngSignature
                        );
            }

            case "image/webp" -> {
                valid = bytes.length >= 12
                        && bytes[0] == 'R'
                        && bytes[1] == 'I'
                        && bytes[2] == 'F'
                        && bytes[3] == 'F'
                        && bytes[8] == 'W'
                        && bytes[9] == 'E'
                        && bytes[10] == 'B'
                        && bytes[11] == 'P';
            }

            default -> valid = false;
        }

        if (!valid) {
            throw new IllegalArgumentException(
                    "Invalid image content or file type mismatch"
            );
        }
    }

    public boolean deleteChatImage(String imageUrl) throws IOException {

        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(imageUrl);

            // Chỉ chấp nhận URL HTTPS Cloudinary
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"res.cloudinary.com".equalsIgnoreCase(uri.getHost())) {
                return false;
            }

            String path = uri.getPath();

            String prefix = "/" + cloudinary.config.cloudName
                    + "/image/upload/";

            if (!path.startsWith(prefix)) {
                return false;
            }

            String remaining = path.substring(prefix.length());

            // Bỏ version Cloudinary
            remaining = remaining.replaceFirst("^v\\d+/", "");

            // Chỉ cho phép xóa ảnh trong thư mục chat
            if (!remaining.startsWith("chatapp/messages/images/")) {
                return false;
            }

            int extensionIndex = remaining.lastIndexOf(".");

            if (extensionIndex == -1) {
                return false;
            }

            String publicId = URLDecoder.decode(
                    remaining.substring(0, extensionIndex),
                    StandardCharsets.UTF_8
            );

            // Chỉ chấp nhận tên UUID do ứng dụng tạo
            String fileId = publicId.substring(
                    "chatapp/messages/images/".length()
            );

            try {
                UUID.fromString(fileId);
            } catch (IllegalArgumentException e) {
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

        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
        
    public String uploadChatFile(MultipartFile file) throws IOException {

        // 1. Kiểm tra file rỗng
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        // 2. Kiểm tra dung lượng
        if (file.getSize() > MAX_CHAT_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size must not exceed 20MB"
            );
        }

        // 3. Lấy tên và phần mở rộng
        String fileName = file.getOriginalFilename();

        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Invalid filename");
        }

        String normalizedName = fileName.replace('\\', '/');
        normalizedName = normalizedName.substring(
                normalizedName.lastIndexOf('/') + 1
        );

        int dotIndex = normalizedName.lastIndexOf('.');

        if (dotIndex <= 0 || dotIndex == normalizedName.length() - 1) {
            throw new IllegalArgumentException(
                    "File extension is required"
            );
        }

        String extension = normalizedName
                .substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);

        // 4. Kiểm tra định dạng cho phép
        if (!ALLOWED_CHAT_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Unsupported file extension: " + extension
            );
        }

        // 5. Đọc dữ liệu
        byte[] fileBytes = file.getBytes();

        // 6. Kiểm tra chữ ký các định dạng có cấu trúc
        validateChatFileSignature(fileBytes, extension);

        // 7. Upload lên Cloudinary
        String publicId = "chatapp/messages/files/"
                + UUID.randomUUID();

        Map<?, ?> uploadResult = cloudinary.uploader().upload(
                fileBytes,
                ObjectUtils.asMap(
                        "public_id", publicId,
                        "resource_type", "raw"
                )
        );

        Object secureUrl = uploadResult.get("secure_url");

        if (secureUrl == null) {
            throw new IOException(
                    "Cloudinary did not return a file URL"
            );
        }

        return secureUrl.toString();
    }

    private void validateChatFileSignature(
            byte[] bytes,
            String extension
    ) {
        boolean valid = switch (extension) {

            case "pdf" -> startsWith(bytes, new byte[]{
                    '%', 'P', 'D', 'F', '-'
            });

            case "doc", "xls", "ppt" -> startsWith(
                    bytes,
                    new byte[]{
                            (byte) 0xD0, (byte) 0xCF,
                            0x11, (byte) 0xE0,
                            (byte) 0xA1, (byte) 0xB1,
                            0x1A, (byte) 0xE1
                    }
            );

            case "docx", "xlsx", "pptx" ->
                    isValidOfficeFile(bytes, extension);

            case "zip" ->
                    startsWith(bytes, new byte[]{
                            'P', 'K', 0x03, 0x04
                    });

            case "rar" -> startsWith(bytes, new byte[]{
                    'R', 'a', 'r', '!', 0x1A, 0x07
            });

            case "7z" -> startsWith(bytes, new byte[]{
                    '7', 'z', (byte) 0xBC,
                    (byte) 0xAF, 0x27, 0x1C
            });

            default -> true;
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    "Invalid file content for extension: " + extension
            );
        }
    }

    private boolean startsWith(byte[] bytes, byte[] signature) {

        if (bytes.length < signature.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) {
                return false;
            }
        }

        return true;
    }


    public boolean deleteChatFile(String fileUrl) throws IOException {

        if (fileUrl == null || fileUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(fileUrl);

            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"res.cloudinary.com".equalsIgnoreCase(uri.getHost())
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                return false;
            }

            String prefix = "/" + cloudinary.config.cloudName
                    + "/raw/upload/";

            String path = uri.getPath();

            if (!path.startsWith(prefix)) {
                return false;
            }

            String remaining = path.substring(prefix.length())
                    .replaceFirst("^v\\d+/", "");

            String folder = "chatapp/messages/files/";

            if (!remaining.startsWith(folder)) {
                return false;
            }

            String fileId = remaining.substring(folder.length());

            // File được upload với public_id là UUID, không có đuôi.
            try {
                UUID.fromString(fileId);
            } catch (IllegalArgumentException e) {
                return false;
            }

            String publicId = folder + fileId;

            Map<?, ?> result = cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap(
                            "resource_type", "raw",
                            "invalidate", true
                    )
            );

            return "ok".equals(result.get("result"));

        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isValidOfficeFile(
            byte[] bytes,
            String extension
    ) {
        if (!startsWith(bytes, new byte[]{
                'P', 'K', 0x03, 0x04
        })) {
            return false;
        }

        String requiredEntry = switch (extension) {
            case "docx" -> "word/document.xml";
            case "xlsx" -> "xl/workbook.xml";
            case "pptx" -> "ppt/presentation.xml";
            default -> "";
        };

        boolean hasContentTypes = false;
        boolean hasRequiredEntry = false;

        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(bytes)
        )) {
            ZipEntry entry;
            int entryCount = 0;

            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > 10000) {
                    return false;
                }

                String name = entry.getName();

                if ("[Content_Types].xml".equals(name)) {
                    hasContentTypes = true;
                }

                if (requiredEntry.equals(name)) {
                    hasRequiredEntry = true;
                }

                zip.closeEntry();
            }

        } catch (IOException e) {
            return false;
        }

        return hasContentTypes && hasRequiredEntry;
    }



}
