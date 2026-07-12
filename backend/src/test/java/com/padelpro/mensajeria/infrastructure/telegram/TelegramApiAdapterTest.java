package com.padelpro.mensajeria.infrastructure.telegram;

import com.padelpro.mensajeria.application.service.TelegramConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@DisplayName("TelegramApiAdapter — real send, no-op without token, fault tolerance (D-OTP-05, RN-TEL-03)")
class TelegramApiAdapterTest {

    private static final String BASE = "http://api.telegram.local";

    @Mock
    private TelegramConfigService configService;

    private TelegramApiAdapter adapterWith(MockRestServiceServer[] serverOut) {
        RestClient.Builder builder = RestClient.builder();
        serverOut[0] = MockRestServiceServer.bindTo(builder).build();
        return new TelegramApiAdapter(configService, builder, BASE);
    }

    @Test
    @DisplayName("sends a sendMessage POST when the bot token is configured")
    void sends_when_token_present() {
        when(configService.getBotToken()).thenReturn(Optional.of("TESTTOKEN"));
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        TelegramApiAdapter adapter = adapterWith(server);
        server[0].expect(requestTo(BASE + "/botTESTTOKEN/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        adapter.enviarMensaje("123", "hola");

        server[0].verify();
    }

    @Test
    @DisplayName("degrades to a no-op (no HTTP call) when no token is configured")
    void noop_without_token() {
        when(configService.getBotToken()).thenReturn(Optional.empty());
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        TelegramApiAdapter adapter = adapterWith(server);

        adapter.enviarMensaje("123", "hola");

        // No expectations set and none required — verify passes proving no request was made.
        server[0].verify();
    }

    @Test
    @DisplayName("swallows an HTTP failure — never breaks the business flow")
    void swallows_http_failure() {
        when(configService.getBotToken()).thenReturn(Optional.of("TESTTOKEN"));
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        TelegramApiAdapter adapter = adapterWith(server);
        server[0].expect(requestTo(BASE + "/botTESTTOKEN/sendMessage"))
                .andRespond(withServerError());

        assertThatCode(() -> adapter.enviarMensaje("123", "hola")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no-op branch does not consult the request factory even if token check is lenient")
    void noop_is_lenient() {
        lenient().when(configService.getBotToken()).thenReturn(Optional.empty());
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        TelegramApiAdapter adapter = adapterWith(server);
        adapter.enviarMensaje("456", "otro");
        server[0].verify();
    }
}
