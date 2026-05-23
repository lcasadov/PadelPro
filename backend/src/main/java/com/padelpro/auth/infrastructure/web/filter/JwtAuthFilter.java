package com.padelpro.auth.infrastructure.web.filter;

import com.padelpro.auth.application.service.JwtService;
import com.padelpro.auth.domain.exception.TokenExpiredException;
import com.padelpro.auth.domain.exception.TokenInvalidException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter — runs once per request.
 *
 * <p>Extracts the {@code Authorization: Bearer <token>} header, validates the JWT
 * with {@link JwtService}, and populates the {@link SecurityContextHolder} so that
 * Spring Security grants access to protected endpoints.
 *
 * <p>On token expiry or invalid signature, the filter clears the security context
 * and lets the request continue unauthenticated (Spring Security will return 401
 * for protected routes).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtService.validateToken(token);
            String subject = claims.getSubject();
            String role = claims.get("role", String.class);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            subject,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (TokenExpiredException | TokenInvalidException ex) {
            // Clear context; Spring Security will reject protected routes with 401
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
