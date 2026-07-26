package com.legalfam.backend.auth.application.service;

import com.legalfam.backend.auth.application.dto.AuthMailDispatch;
import com.legalfam.backend.auth.application.port.in.IAuthMailDispatchUseCase;
import com.legalfam.backend.auth.application.port.in.IAuthUseCase;
import com.legalfam.backend.auth.application.port.out.IAuthMailPort;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Deliberately not transactional. It calls through the {@link IAuthUseCase} proxy, whose token-issuing
 * methods are REQUIRES_NEW, so the token row is committed before the mail port is invoked; otherwise
 * the async send could deliver a link the user clicks before the token is visible to other
 * transactions. The propagation matters: this is also reached from an AFTER_COMMIT listener, where a
 * REQUIRED method would join the already-committed transaction and fail on the first write.
 */
@Service
public class AuthMailDispatchService implements IAuthMailDispatchUseCase {

    private final IAuthUseCase IAuthUseCase;
    private final IAuthMailPort IAuthMailPort;
    private final AuthLinkProperties authLinkProperties;

    public AuthMailDispatchService(
            IAuthUseCase IAuthUseCase,
            IAuthMailPort IAuthMailPort,
            AuthLinkProperties authLinkProperties
    ) {
        this.IAuthUseCase = IAuthUseCase;
        this.IAuthMailPort = IAuthMailPort;
        this.authLinkProperties = authLinkProperties;
    }

    @Override
    public void dispatchEmailVerification(UUID userId) {
        IAuthUseCase.issueEmailVerificationToken(userId).ifPresent(this::sendEmailVerification);
    }

    @Override
    public void resendEmailVerification(String email) {
        IAuthUseCase.issueEmailVerificationToken(email).ifPresent(this::sendEmailVerification);
    }

    @Override
    public void requestPasswordReset(String email) {
        IAuthUseCase.issuePasswordResetToken(email).ifPresent(dispatch ->
                IAuthMailPort.sendPasswordReset(
                        dispatch.email(),
                        dispatch.name(),
                        buildUrl(authLinkProperties.resetPasswordPath(), dispatch.rawToken()),
                        dispatch.expiresInMinutes()
                ));
    }

    private void sendEmailVerification(AuthMailDispatch dispatch) {
        IAuthMailPort.sendEmailVerification(
                dispatch.email(),
                dispatch.name(),
                buildUrl(authLinkProperties.verifyEmailPath(), dispatch.rawToken()),
                dispatch.expiresInMinutes()
        );
    }

    private String buildUrl(String path, String rawToken) {
        String baseUrl = authLinkProperties.frontendBaseUrl();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
