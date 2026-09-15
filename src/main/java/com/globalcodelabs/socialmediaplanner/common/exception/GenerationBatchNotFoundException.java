package com.globalcodelabs.socialmediaplanner.common.exception;

import java.util.UUID;

public class GenerationBatchNotFoundException extends ApplicationException {

    public GenerationBatchNotFoundException(UUID batchId) {
        super(ErrorCode.GENERATION_BATCH_NOT_FOUND, "Generation batch not found: " + batchId);
    }
}
