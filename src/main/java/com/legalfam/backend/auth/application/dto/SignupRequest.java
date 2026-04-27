package com.legalfam.backend.auth.application.dto;

public record SignupRequest(String email, String password, String name, String phone) {
}
