package com.fila.ciclica.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fila.ciclica.domain.Role;
import com.fila.ciclica.domain.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenServiceTest {

    private static final String SECRET = "unit-test-jwt-secret-0123456789abcdefghij";

    private SecretKey secretKey;
    private JwtEncoder jwtEncoder;
    private JwtDecoder jwtDecoder;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        tokenService = new TokenService(jwtEncoder);
    }

    @Test
    @DisplayName("issued token carries subject, role, issued-at and one hour expiration")
    void issuedTokenCarriesExpectedClaims() {
        User user = user("attendant@test.local", Role.ATTENDANT);

        IssuedToken issued = tokenService.issue(user);

        Jwt jwt = jwtDecoder.decode(issued.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("attendant@test.local");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ATTENDANT");
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds())
                .isEqualTo(3600);
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
        assertThat(issued.role()).isEqualTo("ATTENDANT");
        assertThat(issued.expiresIn()).isEqualTo(3600);
        assertThat(tokenService.expiresIn()).isEqualTo(3600);
    }

    @Test
    @DisplayName("token signed with another secret is rejected")
    void tokenWithInvalidSignatureIsRejected() {
        IssuedToken issued = tokenService.issue(user("admin@test.local", Role.ADMIN));

        SecretKey otherKey = new SecretKeySpec(
                "another-unit-test-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        JwtDecoder otherDecoder = NimbusJwtDecoder.withSecretKey(otherKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        assertThatThrownBy(() -> otherDecoder.decode(issued.accessToken()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("expired token is rejected by the decoder")
    void expiredTokenIsRejected() {
        Instant issuedAt = Instant.now().minusSeconds(4000);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("expired@test.local")
                .claim("role", "ADMIN")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    private User user(String email, Role role) {
        User user = new User();
        user.setName("Test User");
        user.setEmail(email);
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setRole(role);
        user.setActive(true);
        return user;
    }
}
