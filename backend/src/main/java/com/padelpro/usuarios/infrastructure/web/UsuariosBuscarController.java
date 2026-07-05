package com.padelpro.usuarios.infrastructure.web;

import com.padelpro.usuarios.application.dto.UserSearchResult;
import com.padelpro.usuarios.application.service.UserSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the registered-partner search (D5).
 *
 * <p>GET /api/usuarios/buscar?q=&lt;term&gt; — search ACTIVE users by name or email, returning a
 * bounded list of {@code {id, nombre}}. Any authenticated user (USER or ADMIN) may call it; the
 * security chain rejects anonymous callers with 401. It deliberately returns only id + display
 * name so it cannot be used to harvest emails or the full member directory (RN-RGPD).
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuariosBuscarController {

    private final UserSearchService userSearchService;

    public UsuariosBuscarController(UserSearchService userSearchService) {
        this.userSearchService = userSearchService;
    }

    /**
     * GET /api/usuarios/buscar?q=&lt;term&gt;
     * Returns matching users (id + display name). A missing or too-short term yields an empty list.
     */
    @GetMapping("/buscar")
    public ResponseEntity<List<UserSearchResult>> buscar(
            @RequestParam(value = "q", required = false, defaultValue = "") String q) {
        return ResponseEntity.ok(userSearchService.search(q));
    }
}
