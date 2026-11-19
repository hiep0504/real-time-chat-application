package com.chatapp.chatapp.message;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

import org.springframework.data.mongodb.core.index.CompoundIndex;

@Document(collection = "messages")
@CompoundIndex(
    name = "conversation_created_idx",
    def = "{'conversationId': 1, 'createdAt': -1}"
)
public class Message {

    @Id
    private String id;

    private String conversationId;
    private String senderId;
    private String type;
    private String content;
    private String fileName;
    private Long fileSize;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean deleted;

    public Message() {
    }

    public Message(
            String conversationId,
            String senderId,
            String type,
            String content,
            String fileName,
            Long fileSize
    ) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.type = type;
        this.content = content;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.createdAt = Instant.now();
        this.updatedAt = null;
        this.deleted = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
