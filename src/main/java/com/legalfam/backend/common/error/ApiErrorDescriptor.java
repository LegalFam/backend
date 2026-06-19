package com.legalfam.backend.common.error;

public interface ApiErrorDescriptor {
    String type();

    String code();

    int status();

    String message();
}
