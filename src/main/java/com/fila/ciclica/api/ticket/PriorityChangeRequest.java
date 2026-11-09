package com.fila.ciclica.api.ticket;

import jakarta.validation.constraints.NotBlank;

public record PriorityChangeRequest(
        @NotBlank(message = "priority is required") String priority) {
}
