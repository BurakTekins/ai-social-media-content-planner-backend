package com.globalcodelabs.socialmediaplanner.infrastructure.budget;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "generation.budget")
public class GenerationBudgetProperties {

    @Min(1)
    private int maxContentsPerBatch = 50;

    @Min(0)
    private int maxImagesPerBatch = 50;

    @Min(0)
    private int maxVideosPerBatch = 10;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal maxEstimatedCostUsd = new BigDecimal("15.00");

    @Valid
    @NotNull
    private EstimatedCostUsd estimatedCostUsd = new EstimatedCostUsd();

    @Getter
    @Setter
    public static class EstimatedCostUsd {

        @NotNull
        @DecimalMin("0.00")
        private BigDecimal textPerItem = new BigDecimal("0.01");

        @NotNull
        @DecimalMin("0.00")
        private BigDecimal imagePerItem = new BigDecimal("0.10");

        @NotNull
        @DecimalMin("0.00")
        private BigDecimal videoPerItem = new BigDecimal("1.00");
    }
}
