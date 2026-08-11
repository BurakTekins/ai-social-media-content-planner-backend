package com.globalcodelabs.socialmediaplanner.infrastructure.ai.video;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "generation.video.recovery")
public class VideoArtifactRecoveryProperties {

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
