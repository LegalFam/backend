package com.legalfam.backend.chat.application.port.out;

import java.util.UUID;

public interface IChatUserLookupPort {
    boolean existsById(UUID userId);
}
