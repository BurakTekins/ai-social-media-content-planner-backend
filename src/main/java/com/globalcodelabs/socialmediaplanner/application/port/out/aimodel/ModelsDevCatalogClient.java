package com.globalcodelabs.socialmediaplanner.application.port.out.aimodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;

import java.util.List;

public interface ModelsDevCatalogClient {

    List<ModelData> fetchAll();

    record ModelData(
            String providerName,
            String modelId,
            String displayName,
            AiCapability capability,
            JsonNode rawMetadata
    ) {
    }
}
