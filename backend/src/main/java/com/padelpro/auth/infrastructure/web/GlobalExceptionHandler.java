package com.padelpro.auth.infrastructure.web;

import com.padelpro.auth.domain.exception.AccountNotActiveException;
import com.padelpro.auth.domain.exception.AuthenticationException;
import com.padelpro.auth.domain.exception.EmailAlreadyExistsException;
import com.padelpro.auth.domain.exception.InvalidPasswordException;
import com.padelpro.auth.domain.exception.TokenExpiredException;
import com.padelpro.auth.domain.exception.TokenInvalidException;
import com.padelpro.auth.infrastructure.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralised exception handler — maps domain exceptions to HTTP responses.
 *
 * <table>
 *   <tr><th>Exception</th><th>HTTP</th><th>error code</th></tr>
 *   <tr><td>InvalidPasswordException</td><td>400</td><td>INVALID_PASSWORD</td></tr>
 *   <tr><td>IllegalArgumentException (missing field)</td><td>400</td><td>MISSING_REQUIRED_FIELD</td></tr>
 *   <tr><td>EmailAlreadyExistsException</td><td>409</td><td>EMAIL_ALREADY_REGISTERED</td></tr>
 *   <tr><td>AuthenticationException</td><td>401</td><td>AUTH_INVALID_CREDENTIALS</td></tr>
 *   <tr><td>AccountNotActiveException</td><td>403</td><td>ACCOUNT_NOT_ACTIVE</td></tr>
 *   <tr><td>TokenExpiredException</td><td>401</td><td>TOKEN_EXPIRED</td></tr>
 *   <tr><td>TokenInvalidException</td><td>401</td><td>TOKEN_INVALID</td></tr>
 * </table>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(InvalidPasswordException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "INVALID_PASSWORD",
                        "Password does not meet policy requirements",
                        ex.getViolations()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleMissingField(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("MISSING_REQUIRED_FIELD", ex.getMessage()));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("EMAIL_ALREADY_REGISTERED",
                        "An account with this email already exists"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("AUTH_INVALID_CREDENTIALS",
                        "Invalid email or password"));
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotActive(AccountNotActiveException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("ACCOUNT_NOT_ACTIVE",
                        "Account is not active"));
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpired(TokenExpiredException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("TOKEN_EXPIRED", "Access token has expired"));
    }

    @ExceptionHandler(TokenInvalidException.class)
    public ResponseEntity<ErrorResponse> handleTokenInvalid(TokenInvalidException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("TOKEN_INVALID", "Access token is invalid"));
    }
}
