package com.sportcourt.core.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void validationError_shouldReturnStandardContract() throws Exception {
        mockMvc.perform(post("/dummy")
                .header("X-Trace-Id", "trace-core-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.traceId").value("trace-core-1"))
            .andExpect(jsonPath("$.error.details[0].field").value("name"));
    }

    @Test
    void responseStatusError_shouldReturnStandardContract() throws Exception {
        mockMvc.perform(get("/dummy/" + UUID.randomUUID())
                .header("X-Trace-Id", "trace-core-2"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.message").value("Resource not found"))
            .andExpect(jsonPath("$.error.traceId").value("trace-core-2"));
    }

    @RestController
    static class DummyController {

        @PostMapping("/dummy")
        void create(@jakarta.validation.Valid @RequestBody DummyRequest request) {
        }

        @GetMapping("/dummy/{id}")
        void get(@PathVariable UUID id) {
            throw new ResponseStatusException(NOT_FOUND, "Resource not found");
        }
    }

    record DummyRequest(@jakarta.validation.constraints.NotBlank String name) {
    }
}
