package com.fila.ciclica.security;

public record IssuedToken(String accessToken, String role, long expiresIn) {
}
