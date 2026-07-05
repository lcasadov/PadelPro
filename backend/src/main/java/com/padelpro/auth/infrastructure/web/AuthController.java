package com.padelpro.auth.infrastructure.web;

import com.padelpro.auth.application.dto.LoginCommand;
import com.padelpro.auth.application.dto.RegisterCommand;
import com.padelpro.auth.application.dto.TokenPair;
import com.padelpro.auth.application.dto.UserDto;
import com.padelpro.auth.application.port.in.LoginUseCase;
import com.padelpro.auth.application.port.in.RefreshTokenUseCase;
import com.padelpro.auth.application.port.in.RegisterUseCase;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the auth-local endpoints.
 *
 * <p>Mapped to {@code /api/auth}.
 *
 * <p>POST /api/auth/register — creates a new PENDING account, returns 201 + UserDto.<br>
 * POST /api/auth/login — authenticates and returns JWT access token (body) +
 *   refresh token (HttpOnly Secure SameSite=Strict cookie).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;

    /** Cookie header applied on login and refresh — scoped to the refresh endpoint only. */
    private static final String REFRESH_COOKIE_TEMPLATE =
            "refresh_token=%s; HttpOnly; Secure; SameSite=Strict; Max-Age=604800; Path=/api/auth/refresh";

    public AuthController(RegisterUseCase registerUseCase, LoginUseCase loginUseCase,
                          RefreshTokenUseCase refreshTokenUseCase) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
    }

    /**
     * Register a new user account.
     *
     * <p>POST /api/auth/register
     *
     * @param command registration data (firstName, lastName, email, password)
     * @return 201 Created with the new {@link UserDto}
     */
    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@RequestBody RegisterCommand command) {
        UserDto created = registerUseCase.register(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Authenticate with email + password and obtain a JWT access token.
     * The refresh token is delivered as an HttpOnly cookie (not in the response body).
     *
     * <p>POST /api/auth/login
     *
     * @param command  login credentials (email, password)
     * @param response HTTP response used to set the HttpOnly refresh-token cookie
     * @return 200 OK with {@link TokenPair} (access_token, token_type, expires_in)
     */
    @PostMapping("/login")
    public ResponseEntity<TokenPair> login(@RequestBody LoginCommand command,
                                           HttpServletResponse response) {
        TokenPair result = loginUseCase.login(command);

        // Set HttpOnly refresh-token cookie (RN-AUTH-10)
        // Path=/api/auth/refresh restricts the cookie to the refresh endpoint only (security-design.md §2.5)
        if (result.rawRefreshToken() != null) {
            response.setHeader("Set-Cookie",
                    String.format(REFRESH_COOKIE_TEMPLATE, result.rawRefreshToken()));
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Renew the access token using the httpOnly {@code refresh_token} cookie, rotating the
     * refresh token in the process.
     *
     * <p>POST /api/auth/refresh — does NOT require an {@code Authorization} header (the access
     * token has expired by design; auth relies on the SameSite=Strict httpOnly cookie + the hash
     * stored in the database).
     *
     * <p>If the cookie is absent, unknown, expired or revoked the use case throws
     * {@link com.padelpro.auth.domain.exception.RefreshTokenInvalidException}, mapped to 401 by
     * {@link GlobalExceptionHandler}.
     *
     * @param rawRefreshToken the raw refresh token from the cookie (absent → null → 401)
     * @param response        HTTP response used to set the rotated refresh-token cookie
     * @return 200 OK with {@link TokenPair} (access_token, token_type, expires_in)
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenPair> refresh(
            @CookieValue(name = "refresh_token", required = false) String rawRefreshToken,
            HttpServletResponse response) {
        TokenPair result = refreshTokenUseCase.refresh(rawRefreshToken);

        // Rotate the cookie with the freshly issued refresh token (same flags as login).
        response.setHeader("Set-Cookie",
                String.format(REFRESH_COOKIE_TEMPLATE, result.rawRefreshToken()));

        return ResponseEntity.ok(result);
    }
}
