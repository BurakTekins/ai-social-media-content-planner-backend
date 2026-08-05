package com.globalcodelabs.socialmediaplanner.infrastructure.config;

import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(PublishingProperties.class)
public class PublishingConfiguration {
}
