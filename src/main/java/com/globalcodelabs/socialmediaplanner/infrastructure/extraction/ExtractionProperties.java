package com.globalcodelabs.socialmediaplanner.infrastructure.extraction;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.extraction")
public class ExtractionProperties {

    @NotNull
    private Duration linkTimeout;

    @Min(1)
    private int maxBodySizeBytes;
}
