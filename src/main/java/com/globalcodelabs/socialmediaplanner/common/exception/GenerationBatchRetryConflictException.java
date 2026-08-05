package com.globalcodelabs.socialmediaplanner.common.exception;

import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatchStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.CONFLICT)
public class GenerationBatchRetryConflictException extends DomainException {

    public GenerationBatchRetryConflictException(UUID batchId, GenerationBatchStatus currentStatus) {
        super("Generation batch can only be retried from FAILED status: "
                + batchId + " (current status: " + currentStatus + ")");
    }
}
