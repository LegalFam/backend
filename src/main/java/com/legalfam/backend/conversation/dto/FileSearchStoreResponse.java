package com.legalfam.backend.conversation.dto;

public record FileSearchStoreResponse(
        String name,
        String displayName,
        String createTime,
        String updateTime
) {
}
