package com.legalfam.backend.auth.infrastructure.adapter.out;

import com.legalfam.backend.auth.application.port.out.IUserPort;
import com.legalfam.backend.auth.domain.model.User;
import com.legalfam.backend.auth.infrastructure.persistence.IUserRepository;
import com.legalfam.backend.common.identity.UserIdentity;
import com.legalfam.backend.common.identity.application.port.out.IUserIdentityPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaUserAdapter implements IUserPort, IUserIdentityPort {

    private final IUserRepository IUserRepository;

    public JpaUserAdapter(IUserRepository IUserRepository) {
        this.IUserRepository = IUserRepository;
    }

    @Override
    public boolean existsByEmail(String email) {
        return IUserRepository.existsByEmail(email);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return IUserRepository.findByEmail(email).map(UserEntityMapper::toDomain);
    }

    @Override
    public Optional<User> findById(UUID userId) {
        return IUserRepository.findById(userId).map(UserEntityMapper::toDomain);
    }

    @Override
    public Optional<UserIdentity> findUserIdentityById(UUID userId) {
        return IUserRepository.findById(userId)
                .map(user -> new UserIdentity(user.getId(), user.getEmail()));
    }

    @Override
    public User save(User user) {
        return UserEntityMapper.toDomain(IUserRepository.save(UserEntityMapper.toEntity(user)));
    }
}
