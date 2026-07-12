package com.padelpro.mensajeria.infrastructure.web;

import com.padelpro.mensajeria.application.service.TelegramWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Telegram Bot webhook (auth-otp-telegram, spec Requirement 1 & 4).
 *
 * <p>{@code POST /api/bot/telegram} — {@code permitAll} in {@code SecurityConfig} (Telegram sends no
 * JWT). Its only protection is the {@code X-Telegram-Bot-Api-Secret-Token} header, validated inside
 * the service (RN-TEL-01): a missing/incorrect secret yields 403 via the global exception handler.
 * A successfully validated update returns 200.
 */
@RestController
@RequestMapping("/api/bot")
public class TelegramWebhookController {

    private final TelegramWebhookService webhookService;

    public TelegramWebhookController(TelegramWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/telegram")
    public ResponseEntity<Void> telegram(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody(required = false) String body) {
        webhookService.handleUpdate(secretToken, body);
        return ResponseEntity.ok().build();
    }
}
