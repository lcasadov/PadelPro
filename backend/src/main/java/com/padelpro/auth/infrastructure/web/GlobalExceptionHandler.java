package com.padelpro.auth.infrastructure.web;

import com.padelpro.auth.domain.exception.AccountNotActiveException;
import com.padelpro.auth.domain.exception.AuthenticationException;
import com.padelpro.auth.domain.exception.EmailAlreadyExistsException;
import com.padelpro.auth.domain.exception.InvalidPasswordException;
import com.padelpro.auth.domain.exception.TokenExpiredException;
import com.padelpro.auth.domain.exception.TokenInvalidException;
import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.auth.infrastructure.web.dto.ErrorResponse;
import com.padelpro.reservas.domain.exception.InvalidReservaStateException;
import com.padelpro.reservas.domain.exception.ReservaForbiddenException;
import com.padelpro.reservas.domain.exception.ReservaNotFoundException;
import com.padelpro.reservas.domain.exception.SlotConflictException;
import com.padelpro.usuarios.domain.exception.AdminSelfDeactivationException;
import com.padelpro.usuarios.domain.exception.EmailConflictException;
import com.padelpro.usuarios.domain.exception.UserNotFoundException;
import com.padelpro.usuarios.domain.exception.UserNotPendingException;
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

    // -------------------------------------------------------------------------
    // usuarios capability
    // -------------------------------------------------------------------------

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("USUARIO_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(UserNotPendingException.class)
    public ResponseEntity<ErrorResponse> handleUserNotPending(UserNotPendingException ex) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("USUARIO_NOT_PENDING", ex.getMessage()));
    }

    @ExceptionHandler(AdminSelfDeactivationException.class)
    public ResponseEntity<ErrorResponse> handleAdminSelfDeactivation(AdminSelfDeactivationException ex) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("ADMIN_SELF_DEACTIVATION", ex.getMessage()));
    }

    @ExceptionHandler(EmailConflictException.class)
    public ResponseEntity<ErrorResponse> handleEmailConflict(EmailConflictException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("USUARIOS_EMAIL_CONFLICT", ex.getMessage()));
    }

    // -------------------------------------------------------------------------
    // auditoria capability
    // -------------------------------------------------------------------------

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", ex.getMessage()));
    }

    // -------------------------------------------------------------------------
    // reservas capability
    // -------------------------------------------------------------------------

    @ExceptionHandler(SlotConflictException.class)
    public ResponseEntity<ErrorResponse> handleSlotConflict(SlotConflictException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(ReservaForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleReservaForbidden(ReservaForbiddenException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(ReservaNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleReservaNotFound(ReservaNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("RESERVA_NOT_FOUND", ex.getMessage()));
    }

    /**
     * Invalid state transitions, cancellations outside the deadline, and the participants-limit
     * breach all surface as 422 with the exception's own machine-readable code (e.g.
     * {@code INVALID_STATE_TRANSITION}, {@code CANCELLATION_DEADLINE_PASSED},
     * {@code PARTICIPANTS_LIMIT_EXCEEDED}).
     */
    @ExceptionHandler(InvalidReservaStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidReservaState(InvalidReservaStateException ex) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }
}
