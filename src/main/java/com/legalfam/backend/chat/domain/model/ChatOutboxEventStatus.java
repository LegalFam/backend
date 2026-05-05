package com.legalfam.backend.chat.domain.model;

public enum ChatOutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED,
    DEAD
}
