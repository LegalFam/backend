package com.legalfam.backend.common.event;

import java.util.UUID;

public record UserRegisteredEvent(UUID userId) {
}
