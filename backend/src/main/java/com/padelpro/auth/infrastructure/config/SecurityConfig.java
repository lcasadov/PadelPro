package com.padelpro.auth.infrastructure.config;

import com.padelpro.auth.infrastructure.config.security.CustomAccessDeniedHandler;
import com.padelpro.auth.infrastructure.config.security.CustomAuthenticationEntryPoint;
import com.padelpro.auth.infrastructure.web.filter.JwtAuthFilter;
import com.padelpro.auth.infrastructure.web.filter.UserStatusFilter;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the auth module.
 *
 * <p>Stateless JWT-based authentication:
 * <ul>
 *   <li>CSRF disabled (stateless API — no session cookies for auth).</li>
 *   <li>Sessions not created.</li>
 *   <li>{@code /api/auth/**} is open; everything else requires a valid JWT.</li>
 *   <li>{@code /api/bot/telegram} and {@code /api/pagos/webhook} are public (webhooks).</li>
 *   <li>{@link JwtAuthFilter} runs before the standard auth filter.</li>
 *   <li>{@link UserStatusFilter} runs after JwtAuthFilter to re-validate account status.</li>
 *   <li>Custom 401/403 handlers return machine-readable JSON instead of HTML.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthFilter jwtAuthFilter,
                                           UserStatusFilter userStatusFilter,
                                           CustomAccessDeniedHandler accessDeniedHandler,
                                           CustomAuthenticationEntryPoint authEntryPoint) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to("health", "info")).permitAll()
                        .requestMatchers("/api/bot/telegram", "/api/pagos/webhook").permitAll()
                        // /api/auth/refresh must be reachable WITHOUT an access token: the access
                        // token has expired by design and renewal relies on the httpOnly cookie
                        // (auth-session-refresh D2). Explicit here even though /api/auth/** covers it.
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/otp/**").authenticated()
                        .requestMatchers("/api/usuarios/me").authenticated()
                        // Partner search (D5): any authenticated user (USER or ADMIN); 401 if anonymous.
                        .requestMatchers("/api/usuarios/buscar").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(accessDeniedHandler)
                        .authenticationEntryPoint(authEntryPoint)
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(userStatusFilter, JwtAuthFilter.class)
                .build();
    }

    /**
     * BCrypt password encoder with cost factor 12 (RN-AUTH-07).
     * Declared as a bean so it can be injected into services.
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Provisional-access policy (D8). The grace window for PENDING accounts is 48h by default,
     * configurable via {@code app.access.provisional-grace-hours}.
     */
    @Bean
    public com.padelpro.auth.domain.model.AccountAccessPolicy accountAccessPolicy(
            @org.springframework.beans.factory.annotation.Value("${app.access.provisional-grace-hours:48}") long graceHours) {
        return new com.padelpro.auth.domain.model.AccountAccessPolicy(
                java.time.Duration.ofHours(graceHours));
    }
}
