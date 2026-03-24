package com.sportcourt.payment.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
                .header("X-Trace-Id", "trace-payment-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.traceId").value("trace-payment-1"))
            .andExpect(jsonPath("$.details[0].field").value("bookingId"));
    }

    @RestController
    static class DummyController {
        @PostMapping("/dummy")
        void create(@jakarta.validation.Valid @RequestBody DummyRequest request) {
        }
    }

    record DummyRequest(@jakarta.validation.constraints.NotNull java.util.UUID bookingId) {
    }
}
