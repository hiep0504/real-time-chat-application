package com.chatapp.chatapp.media;

import com.chatapp.chatapp.message.Message;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/conversations/{conversationId}/media")
public class ChatMediaController {

    private final ChatMediaService chatMediaService;

    public ChatMediaController(
            ChatMediaService chatMediaService
    ) {
        this.chatMediaService = chatMediaService;
    }

    @PostMapping(
            value = "/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Message> sendImage(
            @PathVariable String conversationId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException {

        Message savedMessage = chatMediaService.sendImage(
                authentication.getName(),
                conversationId,
                file
        );

        return ResponseEntity.ok(savedMessage);
    }

    @PostMapping(
            value = "/files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Message> uploadFile(
            @PathVariable String conversationId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException {

        Message message = chatMediaService.sendFile(
                authentication.getName(),
                conversationId,
                file
        );

        return ResponseEntity.ok(message);
    }

}
