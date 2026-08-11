package com.globalcodelabs.socialmediaplanner.infrastructure.config;

import com.globalcodelabs.socialmediaplanner.infrastructure.budget.GenerationBudgetProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.image.ImageGenerationProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.VideoGenerationProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.VideoArtifactRecoveryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        GenerationBudgetProperties.class,
        ImageGenerationProperties.class,
        VideoArtifactRecoveryProperties.class,
        VideoGenerationProperties.class
})
public class GenerationBudgetConfiguration {
}
