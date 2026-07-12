package com.padelpro.mensajeria.infrastructure.web;

import com.padelpro.auth.infrastructure.web.GlobalExceptionHandler;
import com.padelpro.mensajeria.application.service.TelegramWebhookService;
import com.padelpro.mensajeria.domain.exception.TelegramWebhookForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("TelegramWebhookController — POST /api/bot/telegram (RN-TEL-01)")
class TelegramWebhookControllerTest {

    private final TelegramWebhookService service = Mockito.mock(TelegramWebhookService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TelegramWebhookController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("valid secret → 200 and delegates to the service")
    void valid_returns_200() throws Exception {
        mockMvc.perform(post("/api/bot/telegram")
                        .header("X-Telegram-Bot-Api-Secret-Token", "s3cret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":{}}"))
                .andExpect(status().isOk());
        verify(service).handleUpdate(eq("s3cret"), any());
    }

    @Test
    @DisplayName("missing/incorrect secret → 403 TELEGRAM_WEBHOOK_FORBIDDEN")
    void invalid_secret_returns_403() throws Exception {
        doThrow(new TelegramWebhookForbiddenException())
                .when(service).handleUpdate(any(), any());

        mockMvc.perform(post("/api/bot/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":{}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("TELEGRAM_WEBHOOK_FORBIDDEN")));
    }
}
