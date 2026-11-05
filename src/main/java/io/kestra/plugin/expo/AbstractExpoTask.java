package io.kestra.plugin.expo;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Duration;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractExpoTask extends Task {
    @Schema(
        title = "Expo Access Token",
        description = "Optional access token for Expo projects configured with Push Security."
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    protected Property<String> accessToken;

    @Schema(
        title = "Expo Push Endpoint",
        description = "Expo Push API endpoint URL. Defaults to `https://exp.host/--/api/v2/push/send`."
    )
    @PluginProperty(group = "connection")
    @Builder.Default
    protected Property<String> endpoint = Property.ofValue("https://exp.host/--/api/v2/push/send");

    @Schema(
        title = "HTTP Request Timeout",
        description = "Maximum duration to wait for the push notification request to complete."
    )
    @PluginProperty(group = "connection")
    @Builder.Default
    protected Property<Duration> timeout = Property.ofValue(Duration.ofSeconds(30));
}
