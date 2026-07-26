package com.legalfam.backend.auth.application.port.in;

import java.util.UUID;

public interface IAuthMailDispatchUseCase {

    void dispatchEmailVerification(UUID userId);

    void resendEmailVerification(String email);

    void requestPasswordReset(String email);
}
