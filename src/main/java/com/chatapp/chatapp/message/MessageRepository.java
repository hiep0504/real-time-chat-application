package com.chatapp.chatapp.message;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageRepository
        extends MongoRepository<Message, String> {

    // Lấy lịch sử tin nhắn theo conversation
    // Chỉ lấy tin nhắn chưa bị xóa
    Page<Message> findByConversationIdAndDeletedFalse(
            String conversationId,
            Pageable pageable
    );
}