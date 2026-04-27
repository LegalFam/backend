package com.legalfam.backend.auth.infrastructure.adapter.out.persistence;

import com.legalfam.backend.auth.application.port.out.AuthUserPort;
import com.legalfam.backend.user.domain.model.User;
import com.legalfam.backend.user.infrastructure.persistence.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaAuthUserAdapter implements AuthUserPort {

    private final UserRepository userRepository;

    public JpaAuthUserAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public User save(User user) {
        return userRepository.save(user);
    }
}
