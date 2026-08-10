package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.globalcodelabs.socialmediaplanner.application.service.impl.SocialCredentialRefreshService;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "platform-oauth.refresh",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SocialCredentialRefreshJob {

    private static final String JOB_NAME = "social-credential-refresh";

    private final SocialCredentialRefreshService refreshService;

    @Scheduled(fixedDelayString = "${platform-oauth.refresh.fixed-delay:1h}")
    public void refreshExpiringCredentials() {
        long startedAt = System.nanoTime();
        MdcUtil.putJobName(JOB_NAME);
        MdcUtil.putCorrelationId("job-%s-%s".formatted(JOB_NAME, UUID.randomUUID()));
        try {
            int refreshedCount = refreshService.refreshExpiringCredentials();
            log.info(
                    "Social credential refresh job finished refreshedCount={} durationMs={}",
                    refreshedCount,
                    (System.nanoTime() - startedAt) / 1_000_000
            );
        } catch (RuntimeException exception) {
            log.error("Social credential refresh job failed", exception);
        } finally {
            MdcUtil.clear();
        }
    }
}
