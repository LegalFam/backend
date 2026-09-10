package com.legalfam.backend.chat.application.policy;

import com.legalfam.backend.chat.domain.exception.ChatApiError;
import com.legalfam.backend.chat.domain.exception.InvalidChatRequestException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Rechaza los mensajes que traen datos identificables. El frontend aplica el mismo criterio en
 * ChatInput.jsx para avisar antes de enviar; los dos patrones tienen que ir a la par, o el
 * usuario recibe aqui un rechazo despues de que el cliente le dejara pasar.
 *
 * <p>Cada patron pide la forma completa del dato y no un fragmento suelto, porque el falso
 * positivo no molesta: impide consultar. Las fechas (12.05.2024), los numeros de expediente
 * (00123-2024-0-1801-JP-FC-01) y la palabra "calle" o "manzana" en una frase corriente antes
 * cortaban el envio sin que hubiera ningun dato personal.
 */
@Service
public class ChatPrivacyPolicy {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b");

    /** Celular peruano: nueve digitos que empiezan por 9, con +51 y separadores opcionales. */
    private static final Pattern MOBILE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:\\+?51[\\s.-]?)?9\\d{2}[\\s.-]?\\d{3}[\\s.-]?\\d{3}(?!\\d)"
    );

    /** Fijo peruano con prefijo: 014451234, 01 445 1234, (01) 445 1234, 084 123456. */
    private static final Pattern LANDLINE_PATTERN = Pattern.compile(
            "(?<!\\d)0\\d{1,2}(?:[\\s.-]?\\d{6,7}|[\\s.\\-)]\\s?\\d{3}[\\s.-]?\\d{4})(?!\\d)"
    );

    /** DNI y cualquier otro documento de ocho cifras seguidas. */
    private static final Pattern DNI_PATTERN = Pattern.compile("(?<!\\d)\\d{8}(?!\\d)");

    /**
     * Direccion: la palabra sola no basta, tiene que traer un numero cerca y sin guiones de por
     * medio, que es lo que distingue "Av. Arequipa 1234" de un codigo como JR-FC-05.
     */
    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
            "\\b(?:av|avenida|jr|jiron|calle|pasaje|mz|manzana|lote)\\b\\.?[^\\n\\d-]{0,25}?\\d",
            Pattern.CASE_INSENSITIVE
    );

    public void assertAllowed(String messageInput) {
        if (messageInput == null) {
            return;
        }
        if (EMAIL_PATTERN.matcher(messageInput).find()
                || MOBILE_PATTERN.matcher(messageInput).find()
                || LANDLINE_PATTERN.matcher(messageInput).find()
                || DNI_PATTERN.matcher(messageInput).find()
                || ADDRESS_PATTERN.matcher(messageInput).find()) {
            throw InvalidChatRequestException.of(ChatApiError.PERSONAL_DATA_NOT_ALLOWED);
        }
    }
}
