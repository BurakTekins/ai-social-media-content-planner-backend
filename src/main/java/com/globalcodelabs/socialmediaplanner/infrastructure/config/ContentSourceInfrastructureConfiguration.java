package com.globalcodelabs.socialmediaplanner.infrastructure.config;

import com.globalcodelabs.socialmediaplanner.infrastructure.extraction.ExtractionProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({StorageProperties.class, ExtractionProperties.class})
public class ContentSourceInfrastructureConfiguration {
}
