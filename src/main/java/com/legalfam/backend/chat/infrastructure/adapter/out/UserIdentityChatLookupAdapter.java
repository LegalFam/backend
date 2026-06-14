package com.legalfam.backend.chat.infrastructure.adapter.out;

import com.legalfam.backend.auth.application.port.out.IUserPort;
import com.legalfam.backend.chat.application.port.out.IChatUserLookupPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UserIdentityChatLookupAdapter implements IChatUserLookupPort {

    private final IUserPort IUserPort;

    public UserIdentityChatLookupAdapter(IUserPort IUserPort) {
        this.IUserPort = IUserPort;
    }

    @Override
    public boolean existsById(UUID userId) {
        return IUserPort.findById(userId).isPresent();
    }
}
