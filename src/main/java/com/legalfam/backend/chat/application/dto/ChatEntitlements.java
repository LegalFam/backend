package com.legalfam.backend.chat.application.dto;

/**
 * Límites que el plan del usuario impone sobre el chat. Es un tipo propio del módulo
 * de chat: la traducción desde el módulo de pagos ocurre en el adaptador de salida,
 * de modo que chat.application no depende de payment.application.
 *
 * @param contextMessageLimit mensajes previos que se envían al asistente como contexto
 * @param historyWindowDays   antigüedad máxima del historial visible; nulo significa sin límite
 */
public record ChatEntitlements(
        int contextMessageLimit,
        Integer historyWindowDays
) {
}
