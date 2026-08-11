package com.globalcodelabs.socialmediaplanner.infrastructure.oauth;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "platform-oauth.refresh")
public class OAuthRefreshProperties {

    private boolean enabled = true;

    @NotNull
    private Duration fixedDelay = Duration.ofHours(1);

    @NotNull
    private Duration accessTokenSkew = Duration.ofMinutes(5);

    @NotNull
    private Duration instagramRefreshBefore = Duration.ofDays(7);
}
