package com.legalfam.backend.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class AuthenticatedUserResolverTest {

    private final AuthenticatedUserResolver resolver = new AuthenticatedUserResolver();

    @Test
    void requireUserIdReturnsUuidForValidPrincipal() {
        UUID userId = UUID.randomUUID();

        UUID resolved = resolver.requireUserId(" " + userId + " ");

        assertEquals(userId, resolved);
    }

    @Test
    void requireUserIdRejectsMissingAnonymousAndMalformedPrincipals() {
        assertThrows(AccessDeniedException.class, () -> resolver.requireUserId(null));
        assertThrows(AccessDeniedException.class, () -> resolver.requireUserId("   "));
        assertThrows(AccessDeniedException.class, () -> resolver.requireUserId("anonymousUser"));
        assertThrows(AccessDeniedException.class, () -> resolver.requireUserId("not-a-uuid"));
    }

    @Test
    void optionalUserIdReturnsEmptyForMissingOrAnonymousPrincipals() {
        assertTrue(resolver.optionalUserId(null).isEmpty());
        assertTrue(resolver.optionalUserId("   ").isEmpty());
        assertTrue(resolver.optionalUserId("anonymousUser").isEmpty());
    }

    @Test
    void optionalUserIdReturnsUuidForValidPrincipal() {
        UUID userId = UUID.randomUUID();

        Optional<UUID> resolved = resolver.optionalUserId(" " + userId + " ");

        assertTrue(resolved.isPresent());
        assertEquals(userId, resolved.get());
    }

    @Test
    void optionalUserIdRejectsMalformedNonAnonymousPrincipal() {
        assertThrows(AccessDeniedException.class, () -> resolver.optionalUserId("not-a-uuid"));
    }
}
