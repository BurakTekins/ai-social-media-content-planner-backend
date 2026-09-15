package com.globalcodelabs.socialmediaplanner.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiProvider;
import com.globalcodelabs.socialmediaplanner.domain.model.AiModelCache;
import com.globalcodelabs.socialmediaplanner.domain.repository.AiModelCacheRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.budget.GenerationBudgetProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.image.ImageModelPricingRegistry;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.VideoModelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GenerationBudgetPolicy {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);
    private static final int COST_SCALE = 6;

    private final GenerationBudgetProperties properties;
    private final AiModelCacheRepository aiModelCacheRepository;
    private final GeneralSettingsService generalSettingsService;
    private final ImageModelPricingRegistry imageModelPricingRegistry;
    private final VideoModelRegistry videoModelRegistry;

    public void validate(Request request) {
        GeneralSettings settings = generalSettingsService.get();
        validateCounts(request, settings);
        Estimate estimate = estimate(request, settings);
        requireWithinBudget(estimate.totalCostUsd(), settings);
    }

    public void validateSingleGeneration(
            AiCapability capability,
            String provider,
            String model,
            Integer videoDurationSeconds
    ) {
        GeneralSettings settings = generalSettingsService.get();
        BigDecimal estimatedCost = switch (capability) {
            case TEXT -> estimate(new Request(
                    1, false, false, provider, model,
                    null, null, null, null, null
            ), settings).totalCostUsd();
            case IMAGE -> estimateMedia(
                    true,
                    1,
                    provider,
                    model,
                    AiCapability.IMAGE,
                    imageModelPricingRegistry.estimatedOutputTokensPerItem(provider, model)
                            .orElse(properties.getEstimatedImageOutputTokensPerItem()),
                    properties.getEstimatedCostUsd().getImagePerItem()
            ).costUsd();
            case VIDEO -> {
                VideoModelRegistry.VideoModelSpec spec = videoModelRegistry.validate(
                        provider, model, videoDurationSeconds
                );
                yield videoModelRegistry.estimateCostUsd(spec, videoDurationSeconds, 1);
            }
        };
        requireWithinBudget(estimatedCost, settings);
    }

    public Estimate estimate(Request request) {
        GeneralSettings settings = generalSettingsService.get();
        validateCounts(request, settings);
        return estimate(request, settings);
    }

    private Estimate estimate(Request request, GeneralSettings settings) {
        TokenPricing textPricing = resolveTokenPricing(
                request.textProvider(), request.textModel(), AiCapability.TEXT
        ).orElseGet(this::fallbackTextPricing);
        int estimatedInputTokens = Math.multiplyExact(
                request.requestedCount(),
                settings.generationEstimatedInputTokensPerItem()
        );
        int estimatedOutputTokens = Math.multiplyExact(
                request.requestedCount(),
                settings.generationEstimatedOutputTokensPerItem()
        );
        BigDecimal textInputCost = tokenCost(
                estimatedInputTokens,
                textPricing.inputCostUsdPerMillionTokens()
        );
        BigDecimal textOutputCost = tokenCost(
                estimatedOutputTokens,
                textPricing.outputCostUsdPerMillionTokens()
        );
        MediaEstimate imageEstimate = estimateMedia(
                request.includeImage(),
                request.requestedCount(),
                request.imageProvider(),
                request.imageModel(),
                AiCapability.IMAGE,
                imageModelPricingRegistry.estimatedOutputTokensPerItem(
                        request.imageProvider(), request.imageModel()
                ).orElse(properties.getEstimatedImageOutputTokensPerItem()),
                properties.getEstimatedCostUsd().getImagePerItem()
        );
        MediaEstimate videoEstimate = estimateVideo(request);
        BigDecimal totalCost = textInputCost
                .add(textOutputCost)
                .add(imageEstimate.costUsd())
                .add(videoEstimate.costUsd())
                .setScale(COST_SCALE, RoundingMode.HALF_UP);
        return new Estimate(
                totalCost,
                textInputCost,
                textOutputCost,
                imageEstimate.costUsd(),
                videoEstimate.costUsd(),
                estimatedInputTokens,
                estimatedOutputTokens,
                textPricing.inputCostUsdPerMillionTokens(),
                textPricing.outputCostUsdPerMillionTokens(),
                textPricing.source(),
                imageEstimate.source(),
                videoEstimate.source(),
                imageEstimate.inputCostUsdPerMillionTokens(),
                imageEstimate.outputCostUsdPerMillionTokens(),
                videoEstimate.inputCostUsdPerMillionTokens(),
                videoEstimate.outputCostUsdPerMillionTokens(),
                request.includeVideo() ? request.videoDurationSeconds() : null,
                videoEstimate.costUsdPerSecond()
        );
    }

    public Limits limits() {
        GeneralSettings settings = generalSettingsService.get();
        GenerationBudgetProperties.EstimatedCostUsd costs = properties.getEstimatedCostUsd();
        return new Limits(
                settings.generationMaxContentsPerBatch(),
                settings.generationMaxImagesPerBatch(),
                settings.generationMaxVideosPerBatch(),
                settings.generationMaxEstimatedCostUsd(),
                settings.generationEstimatedInputTokensPerItem(),
                settings.generationEstimatedOutputTokensPerItem(),
                properties.getEstimatedMediaInputTokensPerItem(),
                properties.getEstimatedImageOutputTokensPerItem(),
                costs.getFallbackTextInputPerMillionTokens(),
                costs.getFallbackTextOutputPerMillionTokens(),
                costs.getImagePerItem(),
                costs.getVideoPerItem()
        );
    }

    private void validateCounts(Request request, GeneralSettings settings) {
        int requestedCount = request.requestedCount();
        if (requestedCount <= 0) {
            throw new DomainException("Requested content count must be greater than zero");
        }
        if (requestedCount > settings.generationMaxContentsPerBatch()) {
            throw new DomainException(
                    "Requested content count exceeds configured batch limit of "
                            + settings.generationMaxContentsPerBatch()
            );
        }
        if (request.includeImage() && requestedCount > settings.generationMaxImagesPerBatch()) {
            throw new DomainException(
                    "Requested image count exceeds configured batch limit of "
                            + settings.generationMaxImagesPerBatch()
            );
        }
        if (request.includeVideo() && requestedCount > settings.generationMaxVideosPerBatch()) {
            throw new DomainException(
                    "Requested video count exceeds configured batch limit of "
                            + settings.generationMaxVideosPerBatch()
            );
        }
        if (!request.includeVideo() && request.videoDurationSeconds() != null) {
            throw new DomainException("Video duration must be empty when video generation is disabled");
        }
    }

    private MediaEstimate estimateMedia(
            boolean included,
            int requestedCount,
            String provider,
            String model,
            AiCapability capability,
            int estimatedOutputTokensPerItem,
            BigDecimal fallbackCostPerItem
    ) {
        if (!included) {
            return MediaEstimate.notRequested();
        }
        Optional<TokenPricing> pricing = resolveTokenPricing(provider, model, capability);
        if (pricing.isEmpty()) {
            return MediaEstimate.flatRate(
                    fallbackCostPerItem.multiply(BigDecimal.valueOf(requestedCount))
                            .setScale(COST_SCALE, RoundingMode.HALF_UP)
            );
        }
        int estimatedInputTokens = Math.multiplyExact(
                requestedCount,
                properties.getEstimatedMediaInputTokensPerItem()
        );
        int estimatedOutputTokens = Math.multiplyExact(
                requestedCount,
                estimatedOutputTokensPerItem
        );
        TokenPricing tokenPricing = pricing.get();
        BigDecimal estimatedCost = tokenCost(
                estimatedInputTokens,
                tokenPricing.inputCostUsdPerMillionTokens()
        ).add(tokenCost(
                estimatedOutputTokens,
                tokenPricing.outputCostUsdPerMillionTokens()
        )).setScale(COST_SCALE, RoundingMode.HALF_UP);
        return MediaEstimate.modelsDev(estimatedCost, tokenPricing);
    }

    private MediaEstimate estimateVideo(Request request) {
        if (!request.includeVideo()) {
            return MediaEstimate.notRequested();
        }
        VideoModelRegistry.VideoModelSpec spec = videoModelRegistry.validate(
                request.videoProvider(),
                request.videoModel(),
                request.videoDurationSeconds()
        );
        BigDecimal costUsd = videoModelRegistry.estimateCostUsd(
                spec,
                request.videoDurationSeconds(),
                request.requestedCount()
        );
        return MediaEstimate.videoRegistry(costUsd, spec.costUsdPerSecond(), spec.pricingSource());
    }

    private Optional<TokenPricing> resolveTokenPricing(
            String provider,
            String model,
            AiCapability capability
    ) {
        if (provider == null || provider.isBlank() || model == null || model.isBlank()) {
            return Optional.empty();
        }
        Optional<AiModelCache> cachedModel = aiModelCacheRepository
                .findByProviderNameAndModelIdAndCapability(
                        normalizeProvider(provider),
                        model.trim(),
                        capability
                );
        if (cachedModel.isEmpty()) {
            return Optional.empty();
        }
        JsonNode cost = cachedModel.get().rawMetadata().path("cost");
        JsonNode input = cost.path("input");
        JsonNode output = cost.path("output");
        if (!input.isNumber()
                || !output.isNumber()
                || input.decimalValue().signum() < 0
                || output.decimalValue().signum() < 0) {
            return Optional.empty();
        }
        return Optional.of(new TokenPricing(
                input.decimalValue(),
                output.decimalValue(),
                "MODELS_DEV"
        ));
    }

    private static void requireWithinBudget(BigDecimal estimatedCost, GeneralSettings settings) {
        if (estimatedCost.compareTo(settings.generationMaxEstimatedCostUsd()) > 0) {
            throw new DomainException(
                    "Estimated generation cost %s USD exceeds configured budget limit of %s USD"
                            .formatted(
                                    estimatedCost.toPlainString(),
                                    settings.generationMaxEstimatedCostUsd().toPlainString()
                            )
            );
        }
    }

    private TokenPricing fallbackTextPricing() {
        GenerationBudgetProperties.EstimatedCostUsd costs = properties.getEstimatedCostUsd();
        return new TokenPricing(
                costs.getFallbackTextInputPerMillionTokens(),
                costs.getFallbackTextOutputPerMillionTokens(),
                "CONFIG_FALLBACK"
        );
    }

    private static String normalizeProvider(String provider) {
        return AiProvider.canonicalize(provider);
    }

    private static BigDecimal tokenCost(int tokens, BigDecimal pricePerMillionTokens) {
        return BigDecimal.valueOf(tokens)
                .multiply(pricePerMillionTokens)
                .divide(ONE_MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }

    public record Request(
            int requestedCount,
            boolean includeImage,
            boolean includeVideo,
            String textProvider,
            String textModel,
            String imageProvider,
            String imageModel,
            String videoProvider,
            String videoModel,
            Integer videoDurationSeconds
    ) {
    }

    public record Estimate(
            BigDecimal totalCostUsd,
            BigDecimal textInputCostUsd,
            BigDecimal textOutputCostUsd,
            BigDecimal imageCostUsd,
            BigDecimal videoCostUsd,
            int estimatedInputTokens,
            int estimatedOutputTokens,
            BigDecimal textInputCostUsdPerMillionTokens,
            BigDecimal textOutputCostUsdPerMillionTokens,
            String textPricingSource,
            String imagePricingSource,
            String videoPricingSource,
            BigDecimal imageInputCostUsdPerMillionTokens,
            BigDecimal imageOutputCostUsdPerMillionTokens,
            BigDecimal videoInputCostUsdPerMillionTokens,
            BigDecimal videoOutputCostUsdPerMillionTokens,
            Integer videoDurationSeconds,
            BigDecimal videoCostUsdPerSecond
    ) {
    }

    public record Limits(
            int maxContentsPerBatch,
            int maxImagesPerBatch,
            int maxVideosPerBatch,
            BigDecimal maxEstimatedCostUsd,
            int estimatedInputTokensPerItem,
            int estimatedOutputTokensPerItem,
            int estimatedMediaInputTokensPerItem,
            int estimatedImageOutputTokensPerItem,
            BigDecimal fallbackTextInputCostUsdPerMillionTokens,
            BigDecimal fallbackTextOutputCostUsdPerMillionTokens,
            BigDecimal estimatedImageCostUsdPerItem,
            BigDecimal estimatedVideoCostUsdPerItem
    ) {
    }

    private record TokenPricing(
            BigDecimal inputCostUsdPerMillionTokens,
            BigDecimal outputCostUsdPerMillionTokens,
            String source
    ) {
    }

    private record MediaEstimate(
            BigDecimal costUsd,
            String source,
            BigDecimal inputCostUsdPerMillionTokens,
            BigDecimal outputCostUsdPerMillionTokens,
            BigDecimal costUsdPerSecond
    ) {
        private static MediaEstimate notRequested() {
            return new MediaEstimate(BigDecimal.ZERO.setScale(COST_SCALE), "NOT_REQUESTED", null, null, null);
        }

        private static MediaEstimate flatRate(BigDecimal costUsd) {
            return new MediaEstimate(costUsd, "CONFIG_FLAT_RATE_FALLBACK", null, null, null);
        }

        private static MediaEstimate modelsDev(BigDecimal costUsd, TokenPricing pricing) {
            return new MediaEstimate(
                    costUsd,
                    "MODELS_DEV_TOKEN_HEURISTIC",
                    pricing.inputCostUsdPerMillionTokens(),
                    pricing.outputCostUsdPerMillionTokens(),
                    null
            );
        }

        private static MediaEstimate videoRegistry(
                BigDecimal costUsd,
                BigDecimal costUsdPerSecond,
                String pricingSource
        ) {
            return new MediaEstimate(costUsd, pricingSource, null, null, costUsdPerSecond);
        }
    }
}
