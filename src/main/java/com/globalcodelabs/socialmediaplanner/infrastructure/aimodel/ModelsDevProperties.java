package com.globalcodelabs.socialmediaplanner.infrastructure.aimodel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "models-dev")
public class ModelsDevProperties {

    @NotBlank
    private String apiUrl = "https://models.dev/api.json";

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(5);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(30);

    @Valid
    @NotNull
    private Sync sync = new Sync();

    @Getter
    @Setter
    public static class Sync {

        @NotBlank
        private String cron = "0 0 3 * * *";

        @NotBlank
        private String zone = "Europe/Istanbul";
    }
}
