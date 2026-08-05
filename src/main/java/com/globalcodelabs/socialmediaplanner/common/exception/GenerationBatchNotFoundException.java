package com.globalcodelabs.socialmediaplanner.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class GenerationBatchNotFoundException extends RuntimeException {

    public GenerationBatchNotFoundException(UUID batchId) {
        super("Generation batch not found: " + batchId);
    }
}
