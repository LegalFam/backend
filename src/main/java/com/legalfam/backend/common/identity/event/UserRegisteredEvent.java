package com.legalfam.backend.common.identity.event;

import java.util.UUID;

public record UserRegisteredEvent(UUID userId) {
}
