package com.globalcodelabs.socialmediaplanner.infrastructure.config;

import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.x.XOAuthProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.OAuthRefreshProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.instagram.InstagramOAuthProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.linkedin.LinkedInOAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        XOAuthProperties.class,
        LinkedInOAuthProperties.class,
        InstagramOAuthProperties.class,
        OAuthRefreshProperties.class
})
public class XOAuthConfiguration {
}
