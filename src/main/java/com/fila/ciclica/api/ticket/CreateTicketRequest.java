package com.fila.ciclica.api.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank(message = "customerName is required")
        @Size(max = 120, message = "customerName must have at most 120 characters")
        String customerName,

        @Size(max = 20, message = "priority must have at most 20 characters")
        String priority,

        @Size(max = 120, message = "externalReference must have at most 120 characters")
        String externalReference) {
}
