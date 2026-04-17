package com.legalfam.backend.conversation.dto;

public record FileSearchUploadResponse(
        String operationName,
        boolean done,
        String documentName
) {
}
