package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateDraftContentRequest(
        @Size(max = 255) String title,
        String text,
        List<@NotBlank String> hashtags
) {

    @JsonIgnore
    @AssertTrue(message = "At least one of title, text or hashtags must be provided")
    public boolean isUpdatePresent() {
        return title != null || text != null || hashtags != null;
    }
}
