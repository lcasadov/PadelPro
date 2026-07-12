package com.padelpro.otp.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.infrastructure.web.GlobalExceptionHandler;
import com.padelpro.otp.application.service.OtpService;
import com.padelpro.otp.domain.exception.OtpVerificationException;
import com.padelpro.otp.domain.model.OtpType;
import com.padelpro.otp.infrastructure.web.dto.VerificarOtpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("OtpController — POST /api/otp/verificar")
class OtpControllerTest {

    private final OtpService otpService = Mockito.mock(OtpService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OtpController(otpService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("42", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String json(String code, String type) throws Exception {
        return objectMapper.writeValueAsString(new VerificarOtpRequest(code, type));
    }

    @Test
    @DisplayName("returns 200 and verified=true on success")
    void verificar_ok() throws Exception {
        mockMvc.perform(post("/api/otp/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("123456", "RESERVATION_CONFIRM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified", is(true)));

        verify(otpService).verify(eq(42L), eq(OtpType.RESERVATION_CONFIRM), eq("123456"));
    }

    @Test
    @DisplayName("returns 422 with the error code when verification fails")
    void verificar_failure_422() throws Exception {
        doThrow(OtpVerificationException.maxAttempts())
                .when(otpService).verify(eq(42L), eq(OtpType.RESERVATION_CONFIRM), eq("000000"));

        mockMvc.perform(post("/api/otp/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("000000", "RESERVATION_CONFIRM")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("OTP_MAX_ATTEMPTS")));
    }

    @Test
    @DisplayName("returns 400 for an unknown OTP type")
    void verificar_unknown_type_400() throws Exception {
        mockMvc.perform(post("/api/otp/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("123456", "NOPE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
        verifyNoInteractions(otpService);
    }
}
