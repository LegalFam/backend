package com.legalfam.backend.common.identity;

import java.util.UUID;

public record UserIdentity(UUID id, String email) {
}
