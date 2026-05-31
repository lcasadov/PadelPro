package com.padelpro.auth.infrastructure.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.AuthenticationException;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TDD RED — Unit tests for CustomAuthenticationEntryPoint.
 * These tests FAIL until CustomAuthenticationEntryPoint is implemented.
 */
@ExtendWith(MockitoExtension.class)
class CustomAuthenticationEntryPointTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private CustomAuthenticationEntryPoint entryPoint;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    @DisplayName("should_write_401_json_response")
    void should_write_401_json_response() throws Exception {
        AuthenticationException ex = new org.springframework.security.authentication.BadCredentialsException("Unauthorized");

        entryPoint.commence(request, response, ex);

        verify(response).setStatus(401);
        verify(response).setContentType("application/json");
        String body = responseWriter.toString();
        assertThat(body).contains("AUTH_REQUIRED");
    }
}
