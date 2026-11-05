package io.kestra.plugin.expo;

import com.sun.net.httpserver.HttpServer;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class PushNotificationTest {
    @Inject
    private RunContextFactory runContextFactory;

    private HttpServer server;
    private String serverUrl;
    private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private int responseStatus = 200;
    private String responseBody = """
        {
          "data": [
            {
              "status": "ok",
              "id": "12345678-abcd-1234-abcd-1234567890ab"
            }
          ]
        }
        """;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        serverUrl = "http://localhost:" + server.getAddress().getPort() + "/push";

        server.createContext("/push", exchange -> {
            lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendPushNotificationSuccessfullyWhenValidParameters() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "deviceToken", "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]"
        ));

        PushNotification task = PushNotification.builder()
            .endpoint(Property.ofValue(serverUrl))
            .to(Property.ofValue(List.of("{{ deviceToken }}")))
            .title(Property.ofValue("Order Confirmed"))
            .body(Property.ofValue("Your order #1001 is being prepared."))
            .data(Property.ofValue(Map.of("orderId", "1001")))
            .sound(Property.ofValue("default"))
            .badge(Property.ofValue(1))
            .priority(Property.ofValue("high"))
            .build();

        PushNotification.Output output = task.run(runContext);

        assertThat(output).isNotNull();
        assertThat(output.getStatus()).isEqualTo(200);
        assertThat(output.getOkCount()).isEqualTo(1);
        assertThat(output.getErrorCount()).isEqualTo(0);
        assertThat(output.getTickets()).hasSize(1);
        assertThat(output.getTickets().getFirst().getStatus()).isEqualTo("ok");
        assertThat(output.getTickets().getFirst().getId()).isEqualTo("12345678-abcd-1234-abcd-1234567890ab");

        assertThat(lastRequestBody.get()).contains("\"ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]\"");
        assertThat(lastRequestBody.get()).contains("\"Order Confirmed\"");
        assertThat(lastRequestBody.get()).contains("\"Your order #1001 is being prepared.\"");
        assertThat(lastRequestBody.get()).contains("\"orderId\":\"1001\"");
        assertThat(lastRequestBody.get()).contains("\"priority\":\"high\"");
    }

    @Test
    void shouldSendWithBearerTokenWhenPushSecurityEnabled() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "expoToken", "secret_expo_push_token_999"
        ));

        PushNotification task = PushNotification.builder()
            .endpoint(Property.ofValue(serverUrl))
            .accessToken(Property.ofExpression("{{ expoToken }}"))
            .to(Property.ofValue(List.of("ExponentPushToken[abc]")))
            .title(Property.ofValue("Secure Alert"))
            .body(Property.ofValue("Push security test"))
            .build();

        PushNotification.Output output = task.run(runContext);

        assertThat(output).isNotNull();
        assertThat(output.getStatus()).isEqualTo(200);
        assertThat(lastAuthHeader.get()).isEqualTo("Bearer secret_expo_push_token_999");
    }

    @Test
    void shouldHandlePartialFailuresWhenSomeTokensInvalid() throws Exception {
        responseBody = """
            {
              "data": [
                {
                  "status": "ok",
                  "id": "ticket_1"
                },
                {
                  "status": "error",
                  "message": "ExponentPushToken[invalid] is not registered",
                  "details": {
                    "error": "DeviceNotRegistered"
                  }
                }
              ]
            }
            """;

        RunContext runContext = runContextFactory.of();

        PushNotification task = PushNotification.builder()
            .endpoint(Property.ofValue(serverUrl))
            .to(Property.ofValue(List.of("ExponentPushToken[valid]", "ExponentPushToken[invalid]")))
            .title(Property.ofValue("Broadcast"))
            .body(Property.ofValue("Broadcast test"))
            .build();

        PushNotification.Output output = task.run(runContext);

        assertThat(output).isNotNull();
        assertThat(output.getStatus()).isEqualTo(200);
        assertThat(output.getOkCount()).isEqualTo(1);
        assertThat(output.getErrorCount()).isEqualTo(1);
        assertThat(output.getTickets()).hasSize(2);
        assertThat(output.getTickets().get(0).getStatus()).isEqualTo("ok");
        assertThat(output.getTickets().get(1).getStatus()).isEqualTo("error");
        assertThat(output.getTickets().get(1).getError()).isEqualTo("DeviceNotRegistered");
    }

    @Test
    void shouldThrowExceptionWhenHttpErrorReturned() {
        responseStatus = 401;
        responseBody = """
            {
              "errors": [
                {
                  "code": "INVALID_TOKEN",
                  "message": "Invalid Expo authentication token"
                }
              ]
            }
            """;

        RunContext runContext = runContextFactory.of();

        PushNotification task = PushNotification.builder()
            .endpoint(Property.ofValue(serverUrl))
            .to(Property.ofValue(List.of("ExponentPushToken[test]")))
            .title(Property.ofValue("Test"))
            .body(Property.ofValue("Test"))
            .build();

        assertThatThrownBy(() -> task.run(runContext))
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessageContaining("HTTP 401");
    }
}
