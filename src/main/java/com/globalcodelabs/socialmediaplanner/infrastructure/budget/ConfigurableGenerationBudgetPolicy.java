package com.globalcodelabs.socialmediaplanner.infrastructure.budget;

import com.fasterxml.jackson.databind.JsonNode;
import com.globalcodelabs.socialmediaplanner.application.service.GenerationBudgetPolicy;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.AiModelCache;
import com.globalcodelabs.socialmediaplanner.domain.repository.AiModelCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ConfigurableGenerationBudgetPolicy implements GenerationBudgetPolicy {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);
    private static final int COST_SCALE = 6;

    private final GenerationBudgetProperties properties;
    private final AiModelCacheRepository aiModelCacheRepository;

    @Override
    public void validate(Request request) {
        validateCounts(request);
        Estimate estimate = estimate(request);
        if (estimate.totalCostUsd().compareTo(properties.getMaxEstimatedCostUsd()) > 0) {
            throw new DomainException(
                    "Estimated generation cost %s USD exceeds configured budget limit of %s USD"
                            .formatted(
                                    estimate.totalCostUsd().toPlainString(),
                                    properties.getMaxEstimatedCostUsd().toPlainString()
                            )
            );
        }
    }

    @Override
    public Estimate estimate(Request request) {
        validateCounts(request);
        TextPricing textPricing = resolveTextPricing(request.textProvider(), request.textModel());
        int estimatedInputTokens = Math.multiplyExact(
                request.requestedCount(),
                properties.getEstimatedInputTokensPerItem()
        );
        int estimatedOutputTokens = Math.multiplyExact(
                request.requestedCount(),
                properties.getEstimatedOutputTokensPerItem()
        );
        BigDecimal textInputCost = tokenCost(
                estimatedInputTokens,
                textPricing.inputCostUsdPerMillionTokens()
        );
        BigDecimal textOutputCost = tokenCost(
                estimatedOutputTokens,
                textPricing.outputCostUsdPerMillionTokens()
        );
        GenerationBudgetProperties.EstimatedCostUsd costs = properties.getEstimatedCostUsd();
        BigDecimal itemCount = BigDecimal.valueOf(request.requestedCount());
        BigDecimal imageCost = request.includeImage()
                ? costs.getImagePerItem().multiply(itemCount)
                : BigDecimal.ZERO;
        BigDecimal videoCost = request.includeVideo()
                ? costs.getVideoPerItem().multiply(itemCount)
                : BigDecimal.ZERO;
        BigDecimal totalCost = textInputCost
                .add(textOutputCost)
                .add(imageCost)
                .add(videoCost)
                .setScale(COST_SCALE, RoundingMode.HALF_UP);
        return new Estimate(
                totalCost,
                textInputCost,
                textOutputCost,
                imageCost.setScale(COST_SCALE, RoundingMode.HALF_UP),
                videoCost.setScale(COST_SCALE, RoundingMode.HALF_UP),
                estimatedInputTokens,
                estimatedOutputTokens,
                textPricing.inputCostUsdPerMillionTokens(),
                textPricing.outputCostUsdPerMillionTokens(),
                textPricing.source()
        );
    }

    private void validateCounts(Request request) {
        int requestedCount = request.requestedCount();
        if (requestedCount <= 0) {
            throw new DomainException("Requested content count must be greater than zero");
        }
        if (requestedCount > properties.getMaxContentsPerBatch()) {
            throw new DomainException(
                    "Requested content count exceeds configured batch limit of "
                            + properties.getMaxContentsPerBatch()
            );
        }
        if (request.includeImage() && requestedCount > properties.getMaxImagesPerBatch()) {
            throw new DomainException(
                    "Requested image count exceeds configured batch limit of "
                            + properties.getMaxImagesPerBatch()
            );
        }
        if (request.includeVideo() && requestedCount > properties.getMaxVideosPerBatch()) {
            throw new DomainException(
                    "Requested video count exceeds configured batch limit of "
                            + properties.getMaxVideosPerBatch()
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
                properties.getEstimatedInputTokensPerItem(),
                properties.getEstimatedOutputTokensPerItem(),
                costs.getFallbackTextInputPerMillionTokens(),
                costs.getFallbackTextOutputPerMillionTokens(),
                costs.getImagePerItem(),
                costs.getVideoPerItem()
        );
    }

    private TextPricing resolveTextPricing(String provider, String model) {
        GenerationBudgetProperties.EstimatedCostUsd costs = properties.getEstimatedCostUsd();
        if (provider == null || provider.isBlank() || model == null || model.isBlank()) {
            return fallbackPricing(costs);
        }
        Optional<AiModelCache> cachedModel = aiModelCacheRepository
                .findByProviderNameAndModelIdAndCapability(
                        provider.trim().toLowerCase(Locale.ROOT),
                        model.trim(),
                        AiCapability.TEXT
                );
        if (cachedModel.isEmpty()) {
            return fallbackPricing(costs);
        }
        JsonNode cost = cachedModel.get().rawMetadata().path("cost");
        JsonNode input = cost.path("input");
        JsonNode output = cost.path("output");
        if (!input.isNumber() || !output.isNumber()) {
            return fallbackPricing(costs);
        }
        return new TextPricing(
                input.decimalValue(),
                output.decimalValue(),
                "MODELS_DEV"
        );
    }

    private static TextPricing fallbackPricing(
            GenerationBudgetProperties.EstimatedCostUsd costs
    ) {
        return new TextPricing(
                costs.getFallbackTextInputPerMillionTokens(),
                costs.getFallbackTextOutputPerMillionTokens(),
                "CONFIG_FALLBACK"
        );
    }

    private static BigDecimal tokenCost(int tokens, BigDecimal pricePerMillionTokens) {
        return BigDecimal.valueOf(tokens)
                .multiply(pricePerMillionTokens)
                .divide(ONE_MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }

    private record TextPricing(
            BigDecimal inputCostUsdPerMillionTokens,
            BigDecimal outputCostUsdPerMillionTokens,
            String source
    ) {
    }
}
