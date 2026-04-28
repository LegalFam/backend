package com.legalfam.backend.chat.application.port.out;

import java.util.UUID;

public interface ChatUserLookupPort {
    boolean existsById(UUID userId);
}
