package com.globalcodelabs.socialmediaplanner.infrastructure.config;

import com.globalcodelabs.socialmediaplanner.infrastructure.aimodel.ModelsDevProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ModelsDevProperties.class)
public class ModelsDevConfiguration {
}
