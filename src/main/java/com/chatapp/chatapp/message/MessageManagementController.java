package com.chatapp.chatapp.message;

import com.chatapp.chatapp.message.dto.UpdateMessageRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
public class MessageManagementController {

    private final MessageService messageService;

    public MessageManagementController(
            MessageService messageService
    ) {
        this.messageService = messageService;
    }

    // Chỉnh sửa tin nhắn
    @PutMapping("/{messageId}")
    public ResponseEntity<?> updateMessage(
            Authentication authentication,
            @PathVariable String messageId,
            @RequestBody UpdateMessageRequest request
    ) {
        try {

            Message message = messageService.updateMessage(
                    authentication.getName(),
                    messageId,
                    request.content()
            );

            return ResponseEntity.ok(message);

        } catch (SecurityException e) {

            return ResponseEntity.status(403)
                    .body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    // Xóa tin nhắn
    @DeleteMapping("/{messageId}")
    public ResponseEntity<?> deleteMessage(
            Authentication authentication,
            @PathVariable String messageId
    ) {
        try {

            messageService.deleteMessage(
                    authentication.getName(),
                    messageId
            );

            return ResponseEntity.ok(
                    "Message deleted successfully"
            );

        } catch (SecurityException e) {

            return ResponseEntity.status(403)
                    .body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }
}
