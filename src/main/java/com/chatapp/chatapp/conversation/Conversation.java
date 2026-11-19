package com.chatapp.chatapp.conversation;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "conversations")
public class Conversation {

    @Id
    private String id;

    // PRIVATE hoặc GROUP
    private String type;

    // Tên nhóm (null nếu chat cá nhân)
    private String name;

    // Avatar nhóm (null nếu chat cá nhân)
    private String avatar;

    // Danh sách ID thành viên
    private List<String> memberIds;

    // ID người tạo cuộc trò chuyện
    private String createdBy;

    // Thời gian tạo
    private Instant createdAt;

    // Thời gian cập nhật
    private Instant updatedAt;

    // Trạng thái xóa (false nếu chưa xóa, true nếu đã xóa)
    private boolean deleted = false;

    public Conversation() {
    }

    public Conversation(
            String type,
            String name,
            String avatar,
            List<String> memberIds,
            String createdBy
    ) {
        this.type = type;
        this.name = name;
        this.avatar = avatar;
        this.memberIds = memberIds;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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
