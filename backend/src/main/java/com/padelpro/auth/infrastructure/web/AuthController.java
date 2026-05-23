package com.padelpro.auth.infrastructure.web;

import com.padelpro.auth.application.dto.LoginCommand;
import com.padelpro.auth.application.dto.RegisterCommand;
import com.padelpro.auth.application.dto.TokenPair;
import com.padelpro.auth.application.dto.UserDto;
import com.padelpro.auth.application.port.in.LoginUseCase;
import com.padelpro.auth.application.port.in.RegisterUseCase;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
        if (result.rawRefreshToken() != null) {
            Cookie cookie = new Cookie("refresh_token", result.rawRefreshToken());
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setPath("/");
            cookie.setMaxAge(604800); // 7 days
            // SameSite=Strict — set via header because Cookie API doesn't expose it
            response.addCookie(cookie);
            response.setHeader("Set-Cookie",
                    "refresh_token=" + result.rawRefreshToken()
                    + "; HttpOnly; Secure; SameSite=Strict; Max-Age=604800; Path=/");
        }

        return ResponseEntity.ok(result);
    }
}
