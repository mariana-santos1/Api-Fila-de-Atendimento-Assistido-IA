package com.fila.ciclica.common;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ErrorHandlingTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FixtureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("validation failure returns 400 with field errors")
    void validationFailureReturnsFields() throws Exception {
        mockMvc.perform(post("/fixture/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.name").value("name is required"));
    }

    @Test
    @DisplayName("malformed body returns 400 MALFORMED_REQUEST")
    void malformedBodyReturnsMalformedRequest() throws Exception {
        mockMvc.perform(post("/fixture/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("missing resource returns 404 NOT_FOUND")
    void missingResourceReturnsNotFound() throws Exception {
        mockMvc.perform(get("/fixture/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Resource not found"));
    }

    @Test
    @DisplayName("ApiException maps to 409 with its code and detail")
    void apiExceptionMapsToConflict() throws Exception {
        mockMvc.perform(get("/fixture/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.detail").value("Email already exists"));
    }

    @Test
    @DisplayName("unexpected failure returns 500 INTERNAL_ERROR without leaking details")
    void unexpectedFailureReturnsInternalError() throws Exception {
        mockMvc.perform(get("/fixture/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(containsString("boom"))));
    }

    @RestController
    static class FixtureController {

        @PostMapping("/fixture/validate")
        void validate(@Valid @RequestBody FixtureRequest request) {
        }

        @GetMapping("/fixture/missing")
        void missing() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/fixture/missing", "missing");
        }

        @GetMapping("/fixture/conflict")
        void conflict() {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "Email already exists");
        }

        @GetMapping("/fixture/error")
        void error() {
            throw new IllegalStateException("boom");
        }
    }

    record FixtureRequest(@NotBlank(message = "name is required") String name) {
    }
}
