package com.globalcodelabs.socialmediaplanner.application.service;

import java.util.UUID;

public interface GenerationBatchProcessingService {

    void start(UUID batchId);
}
