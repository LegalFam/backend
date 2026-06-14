package com.legalfam.backend.common.identity.application.port.out;

import com.legalfam.backend.common.identity.UserIdentity;
import java.util.Optional;
import java.util.UUID;

public interface IUserIdentityPort {
    Optional<UserIdentity> findUserIdentityById(UUID userId);
}
