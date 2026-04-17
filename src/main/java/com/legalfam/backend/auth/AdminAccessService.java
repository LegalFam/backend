package com.legalfam.backend.auth;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AdminAccessService {

    private static final Logger log = LoggerFactory.getLogger(AdminAccessService.class);

    private final List<String> adminEmails;

    public AdminAccessService(@Value("${app.admin.emails:}") String adminEmailsCsv) {
        this.adminEmails = parseAdminEmails(adminEmailsCsv);
    }

    public void requireAdmin(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            log.warn("Admin access denied: missing authentication");
            throw new AccessDeniedException("Access is forbidden");
        }

        String email = authentication.getName().trim().toLowerCase();
        if (!adminEmails.contains(email)) {
            log.warn("Admin access denied: email={} not in admin list", email);
            throw new AccessDeniedException("Access is forbidden");
        }
        log.info("Admin access granted: email={}", email);
    }

    private List<String> parseAdminEmails(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}
