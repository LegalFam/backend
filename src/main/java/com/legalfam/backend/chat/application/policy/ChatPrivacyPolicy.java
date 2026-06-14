package com.legalfam.backend.chat.application.policy;

import com.legalfam.backend.chat.domain.exception.InvalidChatRequestException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ChatPrivacyPolicy {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\+?51\\s*)?(?:9\\d{2}|0?1|[2-8]\\d)(?:[\\s.-]*\\d){6,8}(?!\\d)");
    private static final Pattern DNI_PATTERN = Pattern.compile("(?<!\\d)\\d{8}(?!\\d)");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
            "\\b(?:av\\.?|avenida|jr\\.?|jiron|calle|pasaje|mz\\.?|manzana|lote)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public void assertAllowed(String messageInput) {
        if (messageInput == null) {
            return;
        }
        if (EMAIL_PATTERN.matcher(messageInput).find()
                || PHONE_PATTERN.matcher(messageInput).find()
                || DNI_PATTERN.matcher(messageInput).find()
                || ADDRESS_PATTERN.matcher(messageInput).find()) {
            throw new InvalidChatRequestException(
                    "Evita enviar datos personales como DNI, telefono, correo o direccion. Describe la situacion de forma general."
            );
        }
    }
}
