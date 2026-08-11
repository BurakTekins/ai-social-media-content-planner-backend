package com.globalcodelabs.socialmediaplanner.infrastructure.ai.image;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "generation.image")
public class ImageGenerationProperties {

    @Valid
    @NotEmpty
    private List<Model> models = new ArrayList<>();

    @Getter
    @Setter
    public static class Model {

        @NotBlank
        private String provider;

        @NotBlank
        private String model;

        @Positive
        private int estimatedOutputTokensPerItem;

        @NotBlank
        private String pricingSource;
    }
}
