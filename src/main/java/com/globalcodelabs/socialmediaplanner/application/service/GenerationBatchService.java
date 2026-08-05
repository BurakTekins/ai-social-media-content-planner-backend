package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.application.command.CreateGenerationBatchCommand;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatchStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface GenerationBatchService {

    GenerationBatch create(CreateGenerationBatchCommand command);

    GenerationBatch get(UUID batchId);

    GenerationBatch retry(UUID batchId);

    void markDispatchFailed(UUID batchId);

    Page<GenerationBatch> list(
            GenerationBatchStatus status,
            Platform platform,
            ContentType contentType,
            Pageable pageable
    );
}
