package com.legalfam.backend.auth.infrastructure.adapter.out.persistence;

import com.legalfam.backend.auth.application.port.out.UserPort;
import com.legalfam.backend.auth.domain.model.User;
import com.legalfam.backend.auth.infrastructure.persistence.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaUserAdapter implements UserPort {

    private final UserRepository userRepository;

    public JpaUserAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email).map(UserEntityMapper::toDomain);
    }

    @Override
    public Optional<User> findById(UUID userId) {
        return userRepository.findById(userId).map(UserEntityMapper::toDomain);
    }

    @Override
    public User save(User user) {
        return UserEntityMapper.toDomain(userRepository.save(UserEntityMapper.toEntity(user)));
    }
}
