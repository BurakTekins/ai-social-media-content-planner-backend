package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import org.springframework.data.domain.Page;

import java.util.List;

public record GenerationBatchPageResponse(
        List<GenerationBatchSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static GenerationBatchPageResponse from(Page<GenerationBatch> batches) {
        return new GenerationBatchPageResponse(
                batches.getContent().stream()
                        .map(GenerationBatchSummaryResponse::from)
                        .toList(),
                batches.getNumber(),
                batches.getSize(),
                batches.getTotalElements(),
                batches.getTotalPages()
        );
    }
}
