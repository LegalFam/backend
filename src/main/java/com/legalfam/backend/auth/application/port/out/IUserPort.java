package com.legalfam.backend.auth.application.port.out;

import com.legalfam.backend.auth.domain.model.User;
import java.util.Optional;
import java.util.UUID;

public interface IUserPort {
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findById(UUID userId);

    User save(User user);
}
