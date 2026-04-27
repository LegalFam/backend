package com.legalfam.backend.auth.application.port.out;

import com.legalfam.backend.user.domain.model.User;
import java.util.Optional;

public interface AuthUserPort {
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    User save(User user);
}
