package com.padelpro.auth.infrastructure.web;

import com.padelpro.auth.application.dto.LoginCommand;
import com.padelpro.auth.application.dto.RegisterCommand;
import com.padelpro.auth.application.port.in.LoginUseCase;
import com.padelpro.auth.application.port.in.RegisterUseCase;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the auth-local endpoints.
 *
 * <p><strong>Wave 2 skeleton</strong> — endpoints declared but not implemented.
 * All handler logic (validation, error mapping, cookie setting for refresh tokens)
 * will be added in Wave 3.
 *
 * <p>Mapped to {@code /api/auth}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;

    public AuthController(RegisterUseCase registerUseCase, LoginUseCase loginUseCase) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
    }

    /**
     * Register a new user account.
     *
     * <p>POST /api/auth/register
     *
     * @param command registration data (firstName, lastName, email, password)
     * @return 201 Created with the new user DTO (Wave 3)
     * @throws UnsupportedOperationException until Wave 3 implementation
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterCommand command) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Authenticate with email + password and obtain a JWT access token.
     * The refresh token is set as an HttpOnly cookie in the response (Wave 3).
     *
     * <p>POST /api/auth/login
     *
     * @param command  login credentials (email, password)
     * @param response HTTP response used to set the HttpOnly refresh-token cookie
     * @return 200 OK with {@link com.padelpro.auth.application.dto.TokenPair} (Wave 3)
     * @throws UnsupportedOperationException until Wave 3 implementation
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginCommand command, HttpServletResponse response) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
