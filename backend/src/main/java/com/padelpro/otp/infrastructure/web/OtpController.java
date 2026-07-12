package com.padelpro.otp.infrastructure.web;

import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.otp.application.service.OtpService;
import com.padelpro.otp.domain.model.OtpType;
import com.padelpro.otp.infrastructure.web.dto.VerificarOtpRequest;
import com.padelpro.otp.infrastructure.web.dto.VerificarOtpResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OTP verification endpoint (auth-otp-telegram, spec Requirement 2).
 *
 * <p>{@code POST /api/otp/verificar} — verify a critical-operation OTP for the authenticated user.
 * Secured by {@code /api/otp/**} → {@code authenticated()} in {@code SecurityConfig} (401 if
 * anonymous). A successful verification returns 200 and marks the code used; a failure surfaces as
 * 422 with the standard error body.
 */
@RestController
@RequestMapping("/api/otp")
public class OtpController {

    private final OtpService otpService;

    public OtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/verificar")
    public ResponseEntity<VerificarOtpResponse> verificar(@Valid @RequestBody VerificarOtpRequest request) {
        OtpType type = parseType(request.type());
        otpService.verify(currentUserId(), type, request.otpCode());
        return ResponseEntity.ok(new VerificarOtpResponse(true));
    }

    private static OtpType parseType(String raw) {
        try {
            return OtpType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Unknown OTP type: " + raw);
        }
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong((String) auth.getPrincipal());
    }
}
