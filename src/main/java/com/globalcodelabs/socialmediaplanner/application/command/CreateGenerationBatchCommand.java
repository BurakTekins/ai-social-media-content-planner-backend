package com.globalcodelabs.socialmediaplanner.application.command;

import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationStrategy;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;

import java.util.List;

public record CreateGenerationBatchCommand(
        Platform platform,
        ContentType contentType,
        String title,
        int requestedCount,
        boolean includeImage,
        boolean includeVideo,
        String textProvider,
        String textModel,
        String imageProvider,
        String imageModel,
        String videoProvider,
        String videoModel,
        GenerationStrategy generationStrategy,
        List<String> links,
        List<UploadedDocument> documents
) {
}
