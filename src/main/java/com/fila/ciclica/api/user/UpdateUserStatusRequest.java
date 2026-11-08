package com.fila.ciclica.api.user;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull(message = "active is required") Boolean active) {
}
