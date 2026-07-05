package com.padelpro.pagos.infrastructure.web;

import com.padelpro.pagos.application.dto.IniciarPagoRequest;
import com.padelpro.pagos.application.dto.IniciarPagoResponse;
import com.padelpro.pagos.application.dto.PagoHistorialResponse;
import com.padelpro.pagos.application.service.IniciarPagoService;
import com.padelpro.pagos.application.service.PagoQueryService;
import com.padelpro.pagos.application.service.ProcesarWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User-facing payment endpoints (pagos-redsys-online).
 *
 * <ul>
 *   <li>{@code POST /api/pagos/iniciar} — start an online Redsys payment (owner only, D5)</li>
 *   <li>{@code POST /api/pagos/webhook} — Redsys server-to-server notification (no JWT, D3)</li>
 *   <li>{@code GET  /api/pagos}         — the caller's own payment history</li>
 * </ul>
 *
 * <p>The webhook is public (allowlisted in {@code SecurityConfig}); its only protection is the HMAC
 * signature validated inside the service, and it always returns 200 (D3).
 */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final IniciarPagoService iniciarPagoService;
    private final ProcesarWebhookService procesarWebhookService;
    private final PagoQueryService pagoQueryService;

    public PagoController(IniciarPagoService iniciarPagoService,
                          ProcesarWebhookService procesarWebhookService,
                          PagoQueryService pagoQueryService) {
        this.iniciarPagoService = iniciarPagoService;
        this.procesarWebhookService = procesarWebhookService;
        this.pagoQueryService = pagoQueryService;
    }

    @PostMapping("/iniciar")
    public ResponseEntity<IniciarPagoResponse> iniciar(@RequestBody IniciarPagoRequest request) {
        return ResponseEntity.ok(iniciarPagoService.iniciar(request.reservaId(), currentUserId()));
    }

    /**
     * Redsys notification (application/x-www-form-urlencoded). Always 200 to avoid pointless retries
     * (D3); the outcome is decided by the HMAC signature inside the service.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestParam(name = "Ds_SignatureVersion", required = false) String signatureVersion,
            @RequestParam(name = "Ds_MerchantParameters", required = false) String merchantParameters,
            @RequestParam(name = "Ds_Signature", required = false) String signature) {
        procesarWebhookService.procesar(signatureVersion, merchantParameters, signature);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<PagoHistorialResponse>> misPagos() {
        return ResponseEntity.ok(pagoQueryService.listForUser(currentUserId()));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong((String) auth.getPrincipal());
    }
}
