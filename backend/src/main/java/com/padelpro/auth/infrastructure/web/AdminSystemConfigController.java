package com.padelpro.auth.infrastructure.web;

import com.padelpro.auth.application.dto.SystemConfigResponse;
import com.padelpro.auth.application.dto.UpdateSystemConfigRequest;
import com.padelpro.auth.application.service.SystemConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for centralized system configuration (admin only).
 * Endpoints: GET/PATCH /api/admin/sistema/config
 * D-CONF-08: Service layer handles all decryption (never in controller).
 */
@RestController
@RequestMapping("/api/admin/sistema")
public class AdminSystemConfigController {

    private final SystemConfigService configService;

    public AdminSystemConfigController(SystemConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemConfigResponse> getConfig() {
        SystemConfigResponse response = configService.getConfig();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemConfigResponse> updateConfig(
            @RequestBody UpdateSystemConfigRequest request) {
        SystemConfigResponse response = configService.updateConfig(request);
        return ResponseEntity.ok(response);
    }
}
