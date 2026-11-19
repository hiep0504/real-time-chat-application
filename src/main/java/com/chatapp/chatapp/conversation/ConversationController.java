package com.chatapp.chatapp.conversation;

import com.chatapp.chatapp.conversation.dto.CreatePrivateConversationRequest;
import com.chatapp.chatapp.conversation.dto.CreateGroupConversationRequest;
import com.chatapp.chatapp.conversation.dto.AddGroupMemberRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService conversationService;

    public ConversationController(
            ConversationService conversationService
    ) {
        this.conversationService = conversationService;
    }

    // 1. Tạo cuộc trò chuyện cá nhân
    @PostMapping("/private")
    public ResponseEntity<?> createPrivateConversation(
            Authentication authentication,
            @RequestBody CreatePrivateConversationRequest request
    ) {
        try {
            Conversation conversation =
                    conversationService.createPrivateConversation(
                            authentication.getName(),
                            request.receiverId()
                    );

            return ResponseEntity.ok(conversation);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 2. Lấy danh sách cuộc trò chuyện của mình
    @GetMapping
    public ResponseEntity<List<Conversation>> getMyConversations(
            Authentication authentication
    ) {
        List<Conversation> conversations =
                conversationService.getMyConversations(
                        authentication.getName()
                );

        return ResponseEntity.ok(conversations);
    }

    // 3. Xem chi tiết cuộc trò chuyện
    @GetMapping("/{id}")
    public ResponseEntity<?> getConversationById(
            Authentication authentication,
            @PathVariable String id
    ) {
        try {
            Conversation conversation =
                    conversationService.getConversationById(
                            authentication.getName(),
                            id
                    );

            return ResponseEntity.ok(conversation);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    // 4. Tạo cuộc trò chuyện nhóm
    @PostMapping("/group")
    public ResponseEntity<?> createGroupConversation(
            Authentication authentication,
            @RequestBody CreateGroupConversationRequest request
    ) {

        try {

            Conversation conversation =
                    conversationService.createGroupConversation(
                            authentication.getName(),
                            request.name(),
                            request.memberIds()
                    );

            return ResponseEntity.status(201).body(conversation);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<?> addGroupMember(
            Authentication authentication,
            @PathVariable String id,
            @RequestBody AddGroupMemberRequest request
    ) {
        try {
            Conversation conversation =
                    conversationService.addGroupMember(
                            authentication.getName(),
                            id,
                            request.userId()
                    );

            return ResponseEntity.ok(conversation);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<?> removeGroupMember(
            Authentication authentication,
            @PathVariable String id,
            @PathVariable String userId
    ) {

        try {

            Conversation conversation =
                    conversationService.removeGroupMember(
                            authentication.getName(),
                            id,
                            userId
                    );

            return ResponseEntity.ok(conversation);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}/members/me")
    public ResponseEntity<?> leaveGroup(
            Authentication authentication,
            @PathVariable String id
    ) {

        try {

            conversationService.leaveGroup(
                    authentication.getName(),
                    id
            );

            return ResponseEntity.ok(
                    "Left group successfully"
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteGroup(
            Authentication authentication,
            @PathVariable String id
    ) {

        try {

            conversationService.deleteGroup(
                    authentication.getName(),
                    id
            );

            return ResponseEntity.ok(
                    "Group deleted successfully"
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
