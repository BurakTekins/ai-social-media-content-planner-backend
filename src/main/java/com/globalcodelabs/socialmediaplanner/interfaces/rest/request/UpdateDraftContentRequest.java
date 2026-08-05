package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpdateDraftContentRequest(
        String text,
        List<@NotBlank String> hashtags
) {

    @JsonIgnore
    @AssertTrue(message = "At least one of text or hashtags must be provided")
    public boolean isUpdatePresent() {
        return text != null || hashtags != null;
    }
}
