package com.legalfam.backend.auth.infrastructure.persistence.mapper;

import com.legalfam.backend.auth.domain.model.User;
import com.legalfam.backend.auth.infrastructure.persistence.entity.UserEntity;

public final class UserEntityMapper {

    private UserEntityMapper() {
    }

    public static User toDomain(UserEntity entity) {
        return User.restore(
                entity.getId(),
                entity.getEmail(),
                entity.getPassword(),
                entity.getName(),
                entity.getPhone(),
                entity.isEmailVerified(),
                entity.getEmailVerifiedAt()
        );
    }

    public static UserEntity toEntity(User domain) {
        UserEntity entity = new UserEntity();
        entity.setId(domain.getId());
        entity.setEmail(domain.getEmail());
        entity.setPassword(domain.getPassword());
        entity.setName(domain.getName());
        entity.setPhone(domain.getPhone());
        entity.setEmailVerified(domain.isEmailVerified());
        entity.setEmailVerifiedAt(domain.getEmailVerifiedAt());
        return entity;
    }
}
