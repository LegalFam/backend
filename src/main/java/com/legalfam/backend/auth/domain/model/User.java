package com.legalfam.backend.auth.domain.model;

import java.util.UUID;

public class User {

    private UUID id;
    private String email;
    private String password;
    private String name;
    private String phone;

    public static User create(String email, String passwordHash, String name, String phone) {
        User user = new User();
        user.email = email;
        user.password = passwordHash;
        user.name = name;
        user.phone = phone;
        return user;
    }

    public static User restore(UUID id, String email, String passwordHash, String name, String phone) {
        User user = create(email, passwordHash, name, phone);
        user.id = id;
        return user;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changePassword(String passwordHash) {
        this.password = passwordHash;
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
}
