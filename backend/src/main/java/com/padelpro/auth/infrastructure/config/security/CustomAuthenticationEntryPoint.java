package com.padelpro.auth.infrastructure.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.infrastructure.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom handler for {@code 401 Unauthorized} responses.
 *
 * <p>Replaces Spring Security's default redirect/HTML error with a machine-readable
 * JSON body ({@link ErrorResponse}) so API clients can parse the error code.
 *
 * <p>Registered in {@link com.padelpro.auth.infrastructure.config.SecurityConfig}
 * via {@code .exceptionHandling(ex -> ex.authenticationEntryPoint(this))}.
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException ex) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                new ErrorResponse("AUTH_REQUIRED", "Authentication required")
        );
    }
}
