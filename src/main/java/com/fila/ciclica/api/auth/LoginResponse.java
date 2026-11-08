package com.fila.ciclica.api.auth;

public record LoginResponse(String accessToken, String role, long expiresIn) {
}
