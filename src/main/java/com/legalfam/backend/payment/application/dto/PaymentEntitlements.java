package com.legalfam.backend.payment.application.dto;

/**
 * Límites de producto que otorga el plan vigente de un usuario.
 *
 * @param contextMessageLimit mensajes previos que se envían al asistente como contexto
 * @param historyWindowDays   antigüedad máxima del historial visible; nulo significa sin límite
 */
public record PaymentEntitlements(
        int contextMessageLimit,
        Integer historyWindowDays
) {
}
