package com.legalfam.backend.chat.infrastructure.adapter.persistence;

import com.legalfam.backend.auth.application.port.out.UserPort;
import com.legalfam.backend.chat.application.port.out.ChatUserLookupPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UserIdentityChatLookupAdapter implements ChatUserLookupPort {

    private final UserPort userPort;

    public UserIdentityChatLookupAdapter(UserPort userPort) {
        this.userPort = userPort;
    }

    @Override
    public boolean existsById(UUID userId) {
        return userPort.findById(userId).isPresent();
    }
}
