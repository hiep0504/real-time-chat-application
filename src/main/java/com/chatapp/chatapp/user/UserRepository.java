package com.chatapp.chatapp.user;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    // Tìm user bằng email
    Optional<User> findByEmail(String email);

    // Kiểm tra email đã tồn tại
    boolean existsByEmail(String email);

    // Kiểm tra username đã tồn tại
    boolean existsByUsername(String username);
}
