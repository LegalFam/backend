package com.legalfam.backend.auth.application.port.out;

import com.legalfam.backend.auth.domain.model.OneTimeToken;
import com.legalfam.backend.auth.domain.model.OneTimeTokenPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IOneTimeTokenPersistencePort {

    OneTimeToken save(OneTimeToken token);

    Optional<OneTimeToken> findByHashAndPurpose(String tokenHash, OneTimeTokenPurpose purpose);

    Optional<Instant> findLatestIssuedAt(UUID userId, OneTimeTokenPurpose purpose);

    void consumeAllFor(UUID userId, OneTimeTokenPurpose purpose, Instant consumedAt);
}
