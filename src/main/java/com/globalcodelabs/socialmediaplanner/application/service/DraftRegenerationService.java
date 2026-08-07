package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.model.MediaType;

import java.util.UUID;

public interface DraftRegenerationService {

    Content regenerateText(UUID contentId, String provider, String model);

    Content regenerateMedia(UUID contentId, MediaType mediaType, String provider, String model);
}
