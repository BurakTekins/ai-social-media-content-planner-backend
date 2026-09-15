package com.globalcodelabs.socialmediaplanner.domain.policy;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationStrategy;

public final class GenerationBatchPolicy {

    private static final int MAX_BASE_TITLE_LENGTH = 240;

    private GenerationBatchPolicy() {
    }

    public static String normalizeTitle(String title) {
        String normalized = DomainValidation.requireText(title, "Generation title cannot be blank");
        if (normalized.length() > MAX_BASE_TITLE_LENGTH) {
            throw new DomainException("Generation title cannot exceed " + MAX_BASE_TITLE_LENGTH + " characters");
        }
        return normalized;
    }

    public static Selection selectStrategy(
            GenerationStrategy requestedStrategy,
            int sourceCount,
            int requestedCount
    ) {
        if (requestedStrategy == null) {
            if (sourceCount == requestedCount) {
                return new Selection(
                        GenerationStrategy.SOURCE_BASED,
                        "Defaulted to SOURCE_BASED because source count (%d) equals requested content count (%d)."
                                .formatted(sourceCount, requestedCount),
                        null
                );
            }
            return new Selection(
                    GenerationStrategy.COMBINED,
                    "Defaulted to COMBINED because source count (%d) differs from requested content count (%d)."
                            .formatted(sourceCount, requestedCount),
                    null
            );
        }

        String warning = requestedStrategy == GenerationStrategy.SOURCE_BASED && sourceCount < requestedCount
                ? ("SOURCE_BASED was explicitly selected with fewer sources (%d) than requested contents (%d); "
                + "sources will be reused round-robin.").formatted(sourceCount, requestedCount)
                : null;
        return new Selection(
                requestedStrategy,
                "User selected %s strategy.".formatted(requestedStrategy),
                warning
        );
    }

    public record Selection(
            GenerationStrategy strategy,
            String reason,
            String warning
    ) {
    }
}
