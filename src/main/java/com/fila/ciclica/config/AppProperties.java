package com.fila.ciclica.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid Security security, @Valid Admin admin) {

    public record Security(@NotBlank @Size(min = 32) String jwtSecret) {
    }

    public record Admin(@NotBlank @Email String email, @NotBlank String password) {
    }
}
