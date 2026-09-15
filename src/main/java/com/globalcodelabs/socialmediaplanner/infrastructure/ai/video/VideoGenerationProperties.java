package com.globalcodelabs.socialmediaplanner.infrastructure.ai.video;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "generation.video")
public class VideoGenerationProperties {

    @Valid
    @NotEmpty
    private List<Model> models = new ArrayList<>();

    private Map<String, @NotNull @DecimalMin(value = "0.000001") BigDecimal> currencyToUsd =
            new LinkedHashMap<>();

    @Valid
    @NotNull
    private Recovery recovery = new Recovery();

    @Getter
    @Setter
    public static class Model {

        @NotBlank
        private String provider;

        @NotBlank
        private String model;

        @NotEmpty
        private List<@NotNull Integer> supportedDurationSeconds = new ArrayList<>();

        @NotNull
        @DecimalMin(value = "0.00")
        private BigDecimal costPerSecond;

        @NotBlank
        private String currency = "USD";

        @NotBlank
        private String pricingSource;
    }

    @Getter
    @Setter
    public static class Recovery {

        private boolean enabled = true;

        @NotNull
        private Duration scanInterval = Duration.ofSeconds(30);

        @NotNull
        private Duration initialBackoff = Duration.ofSeconds(30);

        @NotNull
        private Duration maximumBackoff = Duration.ofMinutes(30);

        @DecimalMin("1.0")
        private double multiplier = 2.0;

        private int batchSize = 20;
    }
}
