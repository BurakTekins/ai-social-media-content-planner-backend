package com.globalcodelabs.socialmediaplanner.common.exception;

import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationBatchStatus;

import java.util.UUID;

public class GenerationBatchRetryConflictException extends DomainException {

    public GenerationBatchRetryConflictException(UUID batchId, GenerationBatchStatus currentStatus) {
        super(
                ErrorCode.GENERATION_BATCH_RETRY_CONFLICT,
                "Generation batch can only be retried from FAILED status: "
                        + batchId + " (current status: " + currentStatus + ")"
        );
    }
}
