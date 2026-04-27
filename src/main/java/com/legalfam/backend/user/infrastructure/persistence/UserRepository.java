package com.legalfam.backend.user.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.legalfam.backend.user.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);
}
