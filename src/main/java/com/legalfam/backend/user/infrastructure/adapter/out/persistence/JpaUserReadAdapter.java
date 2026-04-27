package com.legalfam.backend.user.infrastructure.adapter.out.persistence;

import com.legalfam.backend.user.domain.model.User;
import com.legalfam.backend.user.infrastructure.persistence.UserRepository;
import com.legalfam.backend.user.application.port.out.UserReadPort;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JpaUserReadAdapter implements UserReadPort {

    private final UserRepository userRepository;

    public JpaUserReadAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }
}
