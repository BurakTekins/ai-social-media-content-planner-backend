package com.globalcodelabs.socialmediaplanner.infrastructure.budget;

import com.globalcodelabs.socialmediaplanner.application.service.GenerationBudgetPolicy;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class ConfigurableGenerationBudgetPolicy implements GenerationBudgetPolicy {

    private static final int COST_SCALE = 4;

    private final GenerationBudgetProperties properties;

    @Override
    public void validate(int requestedCount, boolean includeImage, boolean includeVideo) {
        if (requestedCount <= 0) {
            throw new DomainException("Requested content count must be greater than zero");
        }
        if (requestedCount > properties.getMaxContentsPerBatch()) {
            throw new DomainException(
                    "Requested content count exceeds configured batch limit of "
                            + properties.getMaxContentsPerBatch()
            );
        }
        if (includeImage && requestedCount > properties.getMaxImagesPerBatch()) {
            throw new DomainException(
                    "Requested image count exceeds configured batch limit of "
                            + properties.getMaxImagesPerBatch()
            );
        }
        if (includeVideo && requestedCount > properties.getMaxVideosPerBatch()) {
            throw new DomainException(
                    "Requested video count exceeds configured batch limit of "
                            + properties.getMaxVideosPerBatch()
            );
        }

        BigDecimal estimatedCost = estimate(requestedCount, includeImage, includeVideo);
        if (estimatedCost.compareTo(properties.getMaxEstimatedCostUsd()) > 0) {
            throw new DomainException(
                    "Estimated generation cost %s USD exceeds configured budget limit of %s USD"
                            .formatted(
                                    estimatedCost.toPlainString(),
                                    properties.getMaxEstimatedCostUsd().toPlainString()
                            )
            );
        }
    }

    @Override
    public Limits limits() {
        GenerationBudgetProperties.EstimatedCostUsd costs = properties.getEstimatedCostUsd();
        return new Limits(
                properties.getMaxContentsPerBatch(),
                properties.getMaxImagesPerBatch(),
                properties.getMaxVideosPerBatch(),
                properties.getMaxEstimatedCostUsd(),
                costs.getTextPerItem(),
                costs.getImagePerItem(),
                costs.getVideoPerItem()
        );
    }

    private BigDecimal estimate(int requestedCount, boolean includeImage, boolean includeVideo) {
        GenerationBudgetProperties.EstimatedCostUsd costs = properties.getEstimatedCostUsd();
        BigDecimal perItem = costs.getTextPerItem();
        if (includeImage) {
            perItem = perItem.add(costs.getImagePerItem());
        }
        if (includeVideo) {
            perItem = perItem.add(costs.getVideoPerItem());
        }
        return perItem.multiply(BigDecimal.valueOf(requestedCount))
                .setScale(COST_SCALE, RoundingMode.HALF_UP);
    }
}
