package com.legalfam.backend.chat.application.port.out;

import com.legalfam.backend.chat.application.dto.ChatEntitlements;
import java.util.UUID;

public interface IChatEntitlementsPort {
    ChatEntitlements resolveEntitlements(UUID userId);
}
