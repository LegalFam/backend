package com.legalfam.backend.auth.application.port.out;

public interface IAuthMailPort {

    void sendEmailVerification(String toEmail, String recipientName, String verificationUrl, long expiresInMinutes);

    void sendPasswordReset(String toEmail, String recipientName, String resetUrl, long expiresInMinutes);
}
