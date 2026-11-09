package com.fila.ciclica.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fila.ciclica.support.AbstractIntegrationTest;
import com.fila.ciclica.support.ProofReporter;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(ProofReporter.class)
class SecurityIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    @DisplayName("C3 missing or expired JWT returns 401")
    void missingOrExpiredJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));

        Instant issuedAt = Instant.now().minusSeconds(7200);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("expired@test.local")
                .claim("role", "ADMIN")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(60))
                .build();
        String expiredToken = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        SecretKeySpec foreignKey = new SecretKeySpec(
                "foreign-jwt-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        JwtEncoder foreignEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(foreignKey));
        Instant now = Instant.now();
        JwtClaimsSet forgedClaims = JwtClaimsSet.builder()
                .subject("forged@test.local")
                .claim("role", "ADMIN")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        String forgedToken = foreignEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), forgedClaims))
                .getTokenValue();

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("C4 insufficient role returns 403")
    void insufficientRoleReturnsForbidden() throws Exception {
        String adminToken = login("admin@test.local", "admin12345");
        String attendantEmail = "attendant-" + UUID.randomUUID() + "@test.local";
        String attendantPassword = "attendant123";

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Attendant User","email":"%s","password":"%s","role":"ATTENDANT"}
                                """.formatted(attendantEmail, attendantPassword)))
                .andExpect(status().isCreated());

        String attendantToken = login(attendantEmail, attendantPassword);

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + attendantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(body, "$.accessToken");
    }
}
