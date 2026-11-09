package com.fila.ciclica.api.ticket;

/**
 * Distinguishes a fresh ticket (HTTP 201) from an idempotent replay (HTTP 200).
 */
public record TicketCreationResult(TicketResponse response, boolean created) {
}
