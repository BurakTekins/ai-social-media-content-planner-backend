package com.globalcodelabs.socialmediaplanner.application.command;

import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;

import java.util.List;

public record CreateGenerationBatchCommand(
        Platform platform,
        ContentType contentType,
        int requestedCount,
        boolean includeImage,
        boolean includeVideo,
        String textProvider,
        String textModel,
        String imageProvider,
        String imageModel,
        String videoProvider,
        String videoModel,
        List<String> links,
        List<UploadedDocument> documents
) {
}
