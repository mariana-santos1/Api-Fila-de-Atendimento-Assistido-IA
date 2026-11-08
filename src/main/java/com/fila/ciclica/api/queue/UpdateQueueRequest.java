package com.fila.ciclica.api.queue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Prefix is intentionally immutable after creation so ticket codes stay consistent.
 */
public record UpdateQueueRequest(
        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must have at most 120 characters")
        String name,

        @Size(max = 500, message = "description must have at most 500 characters")
        String description,

        @NotNull(message = "weightNormal is required")
        Integer weightNormal,

        @NotNull(message = "weightPreferential is required")
        Integer weightPreferential,

        @NotNull(message = "weightUrgent is required")
        Integer weightUrgent) {
}
