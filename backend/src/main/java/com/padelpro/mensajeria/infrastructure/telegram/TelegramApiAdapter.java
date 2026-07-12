package com.padelpro.mensajeria.infrastructure.telegram;

import com.padelpro.mensajeria.application.service.TelegramConfigService;
import com.padelpro.mensajeria.domain.port.out.TelegramPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/**
 * Real Telegram Bot API adapter for {@link TelegramPort} (D-OTP-05).
 *
 * <p>Sends a {@code sendMessage} call to {@code https://api.telegram.org/bot<token>/sendMessage}
 * using the bot token decrypted from {@code system_config}. Fault tolerance (RN-TEL-03):
 * <ul>
 *   <li>No token configured → no-op (logged, returns cleanly) so unconfigured environments never
 *       break the business flow.</li>
 *   <li>An HTTP failure is swallowed and logged — sending a message never propagates an exception.</li>
 * </ul>
 * The message text is not logged (RN-RGPD-04): only the chat id and outcome.
 */
public class TelegramApiAdapter implements TelegramPort {

    private static final Logger log = LoggerFactory.getLogger(TelegramApiAdapter.class);

    private final TelegramConfigService configService;
    private final RestClient restClient;
    private final String apiBaseUrl;

    public TelegramApiAdapter(TelegramConfigService configService,
                              RestClient.Builder restClientBuilder,
                              String apiBaseUrl) {
        this.configService = configService;
        this.restClient = restClientBuilder.build();
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
    }

    @Override
    public void enviarMensaje(String chatId, String texto) {
        Optional<String> token = configService.getBotToken();
        if (token.isEmpty()) {
            // RN-TEL-03: degrade to no-op when the bot is not configured.
            log.info("Telegram not configured — skipping message to chat {} (no-op)", chatId);
            return;
        }
        try {
            restClient.post()
                    .uri(apiBaseUrl + "/bot" + token.get() + "/sendMessage")
                    .body(Map.of("chat_id", chatId, "text", texto))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Telegram message sent to chat {}", chatId);
        } catch (Exception ex) {
            // Never break the business flow on a delivery failure (D-OTP-05).
            log.warn("Failed to send Telegram message to chat {}: {}", chatId, ex.getClass().getSimpleName());
        }
    }

    private static String stripTrailingSlash(String url) {
        return (url != null && url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
    }
}
