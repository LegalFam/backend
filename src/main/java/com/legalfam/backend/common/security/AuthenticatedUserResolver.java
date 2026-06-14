package com.legalfam.backend.common.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserResolver {

    public UUID requireUserId(String principalUserId) {
        return parse(principalUserId)
                .orElseThrow(() -> new AccessDeniedException("Access is forbidden"));
    }

    public Optional<UUID> optionalUserId(String principalUserId) {
        if (principalUserId == null || principalUserId.isBlank()) {
            return Optional.empty();
        }
        return parse(principalUserId);
    }

    private Optional<UUID> parse(String principalUserId) {
        if (principalUserId == null || principalUserId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(principalUserId.trim()));
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Access is forbidden", ex);
        }
    }
}
