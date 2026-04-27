package com.legalfam.backend.user.application.service;

import com.legalfam.backend.user.application.port.in.UserQueryUseCase;
import com.legalfam.backend.user.application.port.out.UserReadPort;
import com.legalfam.backend.user.application.dto.UserResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserQueryUseCase {

    private final UserReadPort userReadPort;

    public UserService(UserReadPort userReadPort) {
        this.userReadPort = userReadPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userReadPort.findAll().stream()
                .map(user -> new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getPhone()))
                .toList();
    }
}
