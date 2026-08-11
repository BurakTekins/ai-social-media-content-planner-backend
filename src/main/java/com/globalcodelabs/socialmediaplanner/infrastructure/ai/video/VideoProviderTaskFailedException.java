package com.globalcodelabs.socialmediaplanner.infrastructure.ai.video;

import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;

public class VideoProviderTaskFailedException extends AiProviderResponseException {

    public VideoProviderTaskFailedException(
            String message,
            String providerResponseId,
            String providerRequestId
    ) {
        super(message, providerResponseId, providerRequestId);
    }
}
