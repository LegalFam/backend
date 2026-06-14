package com.legalfam.backend.auth.infrastructure.adapter.out;

import com.legalfam.backend.auth.domain.model.User;
import com.legalfam.backend.auth.infrastructure.persistence.entity.UserEntity;

final class UserEntityMapper {

    private UserEntityMapper() {
    }

    static User toDomain(UserEntity entity) {
        User user = new User();
        user.setId(entity.getId());
        user.setEmail(entity.getEmail());
        user.setPassword(entity.getPassword());
        user.setName(entity.getName());
        user.setPhone(entity.getPhone());
        return user;
    }

    static UserEntity toEntity(User domain) {
        UserEntity entity = new UserEntity();
        entity.setId(domain.getId());
        entity.setEmail(domain.getEmail());
        entity.setPassword(domain.getPassword());
        entity.setName(domain.getName());
        entity.setPhone(domain.getPhone());
        return entity;
    }
}
