package io.kestra.plugin.expo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send push notifications via Expo Push Service",
    description = "Dispatches push notifications to React Native and mobile applications using Expo Push Service."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a push notification to an Expo device token",
            full = true,
            code = """
                id: send_expo_notification
                namespace: company.team

                tasks:
                  - id: send_push
                    type: io.kestra.plugin.expo.PushNotification
                    to:
                      - "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]"
                    title: "Order Shipped"
                    body: "Your package is on its way."
                    data:
                      orderId: "ORD-98765"
                      trackingUrl: "https://example.com/track/ORD-98765"
                    sound: "default"
                    badge: 1
                    priority: "high"
                """
        ),
        @Example(
            title = "Send an alert to multiple devices with Push Security access token",
            full = true,
            code = """
                id: secure_expo_notification
                namespace: company.team

                tasks:
                  - id: send_alert
                    type: io.kestra.plugin.expo.PushNotification
                    accessToken: "{{ secret('EXPO_ACCESS_TOKEN') }}"
                    to:
                      - "ExponentPushToken[token_one]"
                      - "ExponentPushToken[token_two]"
                    title: "System Alert"
                    body: "High memory utilization detected on primary worker node."
                    channelId: "alerts"
                    priority: "high"
                """
        )
    }
)
public class PushNotification extends AbstractExpoTask implements RunnableTask<PushNotification.Output> {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Schema(
        title = "Recipient Push Tokens",
        description = "One or more recipient Expo push tokens formatted as `ExponentPushToken[...]`."
    )
    @PluginProperty(group = "destination")
    @NotNull
    private Property<List<String>> to;

    @Schema(
        title = "Notification Title",
        description = "Title of the push notification."
    )
    @PluginProperty(group = "main")
    private Property<String> title;

    @Schema(
        title = "Notification Subtitle",
        description = "Subtitle of the push notification on iOS."
    )
    @PluginProperty(group = "main")
    private Property<String> subtitle;

    @Schema(
        title = "Notification Body",
        description = "Body text of the push notification."
    )
    @PluginProperty(group = "main")
    private Property<String> body;

    @Schema(
        title = "Custom Data",
        description = "Arbitrary JSON data object attached to the push notification."
    )
    @PluginProperty(group = "main")
    private Property<Map<String, Object>> data;

    @Schema(
        title = "Sound",
        description = "Sound to play when the notification is delivered (`default` or custom sound asset)."
    )
    @PluginProperty(group = "main")
    private Property<String> sound;

    @Schema(
        title = "Badge Count",
        description = "Number to display as the application badge icon."
    )
    @PluginProperty(group = "main")
    private Property<Integer> badge;

    @Schema(
        title = "Android Channel ID",
        description = "Android NotificationChannel identifier for Android 8.0+."
    )
    @PluginProperty(group = "advanced")
    private Property<String> channelId;

    @Schema(
        title = "iOS Category ID",
        description = "Category identifier for iOS interactive notifications."
    )
    @PluginProperty(group = "advanced")
    private Property<String> categoryId;

    @Schema(
        title = "Delivery Priority",
        description = "Delivery priority (`default`, `normal`, or `high`)."
    )
    @PluginProperty(group = "advanced")
    private Property<String> priority;

    @Schema(
        title = "Time to Live",
        description = "Time to live in seconds if the recipient device is offline."
    )
    @PluginProperty(group = "advanced")
    private Property<Long> ttl;

    @Schema(
        title = "Expiration Timestamp",
        description = "Unix timestamp in seconds when the notification should expire."
    )
    @PluginProperty(group = "advanced")
    private Property<Long> expiration;

    @Schema(
        title = "Mutable Content",
        description = "Whether iOS Notification Service Extension can modify the message content."
    )
    @PluginProperty(group = "advanced")
    private Property<Boolean> mutableContent;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        List<String> rawTokens = runContext.render(to).asList(String.class);
        if (rawTokens == null || rawTokens.isEmpty()) {
            throw new IllegalArgumentException("The 'to' field must contain at least one recipient push token.");
        }

        List<String> renderedTo = new ArrayList<>(rawTokens.size());
        for (String rawToken : rawTokens) {
            renderedTo.add(runContext.render(rawToken));
        }

        Map<String, Object> payload = new HashMap<>();
        if (renderedTo.size() == 1) {
            payload.put("to", renderedTo.getFirst());
        } else {
            payload.put("to", renderedTo);
        }

        if (title != null) {
            payload.put("title", runContext.render(title).as(String.class).orElse(null));
        }
        if (subtitle != null) {
            payload.put("subtitle", runContext.render(subtitle).as(String.class).orElse(null));
        }
        if (body != null) {
            payload.put("body", runContext.render(body).as(String.class).orElse(null));
        }
        if (data != null) {
            payload.put("data", runContext.render(data).asMap(String.class, Object.class));
        }
        if (sound != null) {
            payload.put("sound", runContext.render(sound).as(String.class).orElse(null));
        }
        if (badge != null) {
            payload.put("badge", runContext.render(badge).as(Integer.class).orElse(null));
        }
        if (channelId != null) {
            payload.put("channelId", runContext.render(channelId).as(String.class).orElse(null));
        }
        if (categoryId != null) {
            payload.put("categoryId", runContext.render(categoryId).as(String.class).orElse(null));
        }
        if (priority != null) {
            payload.put("priority", runContext.render(priority).as(String.class).orElse(null));
        }
        if (ttl != null) {
            payload.put("ttl", runContext.render(ttl).as(Long.class).orElse(null));
        }
        if (expiration != null) {
            payload.put("expiration", runContext.render(expiration).as(Long.class).orElse(null));
        }
        if (mutableContent != null) {
            payload.put("mutableContent", runContext.render(mutableContent).as(Boolean.class).orElse(null));
        }

        String jsonPayload = MAPPER.writeValueAsString(payload);
        String targetEndpoint = runContext.render(endpoint).as(String.class).orElse("https://exp.host/--/api/v2/push/send");
        Duration reqTimeout = runContext.render(timeout).as(Duration.class).orElse(Duration.ofSeconds(30));

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(targetEndpoint))
            .timeout(reqTimeout)
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8));

        if (accessToken != null) {
            String token = runContext.render(accessToken).as(String.class).orElse(null);
            if (token != null && !token.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }
        }

        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(reqTimeout)
            .build();

        logger.debug("Dispatching push notification to {} Expo recipient(s)", renderedTo.size());
        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        int statusCode = response.statusCode();
        String responseBody = response.body();

        if (statusCode >= 400) {
            throw new KestraRuntimeException(
                String.format("Expo Push API request failed with HTTP %d: %s", statusCode, responseBody)
            );
        }

        JsonNode rootNode = MAPPER.readTree(responseBody);
        List<Ticket> tickets = new ArrayList<>();
        int okCount = 0;
        int errorCount = 0;

        if (rootNode.has("data")) {
            JsonNode dataNode = rootNode.get("data");
            if (dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    Ticket ticket = parseTicket(item);
                    tickets.add(ticket);
                    if ("ok".equalsIgnoreCase(ticket.getStatus())) {
                        okCount++;
                    } else {
                        errorCount++;
                    }
                }
            } else if (dataNode.isObject()) {
                Ticket ticket = parseTicket(dataNode);
                tickets.add(ticket);
                if ("ok".equalsIgnoreCase(ticket.getStatus())) {
                    okCount++;
                } else {
                    errorCount++;
                }
            }
        }

        logger.info("Expo Push notification completed with {} delivered and {} error(s)", okCount, errorCount);

        return Output.builder()
            .status(statusCode)
            .tickets(tickets)
            .okCount(okCount)
            .errorCount(errorCount)
            .build();
    }

    private Ticket parseTicket(JsonNode item) {
        String status = item.has("status") ? item.get("status").asText() : null;
        String id = item.has("id") ? item.get("id").asText() : null;
        String message = item.has("message") ? item.get("message").asText() : null;
        String error = null;
        if (item.has("details") && item.get("details").has("error")) {
            error = item.get("details").get("error").asText();
        }
        return Ticket.builder()
            .status(status)
            .id(id)
            .message(message)
            .error(error)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "HTTP Status Code",
            description = "HTTP status code returned by the Expo Push API."
        )
        private final Integer status;

        @Schema(
            title = "Ticket Receipts",
            description = "List of delivery tickets returned by Expo for each recipient token."
        )
        private final List<Ticket> tickets;

        @Schema(
            title = "Successful Delivery Count",
            description = "Number of notifications accepted by Expo for delivery."
        )
        private final Integer okCount;

        @Schema(
            title = "Failed Delivery Count",
            description = "Number of notifications rejected or failing validation."
        )
        private final Integer errorCount;
    }

    @Builder
    @Getter
    public static class Ticket {
        @Schema(
            title = "Ticket Status",
            description = "Delivery status (`ok` or `error`)."
        )
        private final String status;

        @Schema(
            title = "Receipt Identifier",
            description = "Unique receipt ID for checking delivery confirmation status."
        )
        private final String id;

        @Schema(
            title = "Error Message",
            description = "Human-readable description of the error if status is error."
        )
        private final String message;

        @Schema(
            title = "Error Code",
            description = "Error code returned in ticket details."
        )
        private final String error;
    }
}
