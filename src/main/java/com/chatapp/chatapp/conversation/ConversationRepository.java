package com.chatapp.chatapp.conversation;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository
        extends MongoRepository<Conversation, String> {

    List<Conversation>
    findByMemberIdsContainingAndDeletedFalseOrderByUpdatedAtDesc(
            String userId
    );

    List<Conversation>
    findByTypeAndMemberIdsContainingAndDeletedFalse(
            String type,
            String userId
    );

    Optional<Conversation>
    findByIdAndMemberIdsContainingAndDeletedFalse(
            String id,
            String userId
    );

    Optional<Conversation> findByIdAndDeletedFalse(
            String id
    );
}