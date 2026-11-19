package com.chatapp.chatapp.message;
import com.chatapp.chatapp.message.dto.SendMessageRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {
    
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    // API gửi tin nhắn văn bản
    @PostMapping
    public ResponseEntity<?> sendTextMessage(
            Authentication authentication,
            @PathVariable String conversationId,
            @RequestBody SendMessageRequest request
    ) {

        try {

            Message message = messageService.sendTextMessage(
                    authentication.getName(),
                    conversationId,
                    request.content()
            );

            return ResponseEntity.status(201).body(message);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getMessages(
            Authentication authentication,
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {

        try {

            Page<Message> messages =
                    messageService.getMessages(
                            authentication.getName(),
                            conversationId,
                            page,
                            size
                    );

            return ResponseEntity.ok(messages);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

}
