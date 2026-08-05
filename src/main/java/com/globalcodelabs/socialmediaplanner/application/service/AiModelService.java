package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.application.port.out.aimodel.ModelsDevCatalogClient.ModelData;
import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.AiModelCache;

import java.time.OffsetDateTime;
import java.util.List;

public interface AiModelService {

    List<AiModelCache> findAll(AiCapability capability, String provider);

    int synchronize(List<ModelData> models, OffsetDateTime syncedAt);
}
