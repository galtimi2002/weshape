package com.weshape.backend.repository;

import com.weshape.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByFirebaseUid(String firebaseUid);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUserCode(String userCode);

    boolean existsByUserCode(String userCode);
}
