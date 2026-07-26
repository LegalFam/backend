package com.legalfam.backend.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public class User {

    private UUID id;
    private String email;
    private String password;
    private String name;
    private String phone;
    private boolean emailVerified;
    private Instant emailVerifiedAt;

    public static User create(String email, String passwordHash, String name, String phone) {
        User user = new User();
        user.email = email;
        user.password = passwordHash;
        user.name = name;
        user.phone = phone;
        user.emailVerified = false;
        return user;
    }

    public static User restore(
            UUID id,
            String email,
            String passwordHash,
            String name,
            String phone,
            boolean emailVerified,
            Instant emailVerifiedAt
    ) {
        User user = create(email, passwordHash, name, phone);
        user.id = id;
        user.emailVerified = emailVerified;
        user.emailVerifiedAt = emailVerifiedAt;
        return user;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changePassword(String passwordHash) {
        this.password = passwordHash;
    }

    public void verifyEmail(Instant now) {
        if (!emailVerified) {
            this.emailVerified = true;
            this.emailVerifiedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }
    public String getEmail() {
        return email;
    }
    public String getPassword() {
        return password;
    }
    public String getName() {
        return name;
    }
    public String getPhone() {
        return phone;
    }
    public boolean isEmailVerified() {
        return emailVerified;
    }
    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }
}
