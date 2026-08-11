package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateContentRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull Platform platform,
        @NotNull ContentType contentType,
        @NotBlank String text,
        List<@NotBlank String> hashtags,
        List<@Valid CreateContentMediaRequest> media,
        UUID batchId
) {
}
