package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateContentRequest(
        @NotNull Platform platform,
        @NotNull ContentType contentType,
        @NotBlank String text,
        List<@NotBlank String> hashtags,
        List<@Valid CreateContentMediaRequest> media,
        UUID batchId
) {
}
