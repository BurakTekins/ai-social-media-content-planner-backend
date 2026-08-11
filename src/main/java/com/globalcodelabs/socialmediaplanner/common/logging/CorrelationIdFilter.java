package com.globalcodelabs.socialmediaplanner.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/actuator/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MdcUtil.putCorrelationId(correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        long startedAt = System.nanoTime();
        boolean requestFailed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            requestFailed = true;
            log.error(
                    "HTTP request failed method={} path={} durationMs={} errorType={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    elapsedMilliseconds(startedAt),
                    exception.getClass().getSimpleName(),
                    exception
            );
            throw exception;
        } finally {
            if (!requestFailed && request.getRequestURI().startsWith("/api/")) {
                logCompletion(request, response, startedAt);
            }
            MdcUtil.clear();
        }
    }

    private static void logCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            long startedAt
    ) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        int status = response.getStatus();
        long durationMs = elapsedMilliseconds(startedAt);
        if (status >= 500) {
            log.debug(
                    "HTTP request completed method={} path={} status={} durationMs={}",
                    method, path, status, durationMs
            );
        } else if (status >= 400) {
            log.warn(
                    "HTTP request completed method={} path={} status={} durationMs={}",
                    method, path, status, durationMs
            );
        } else if ("GET".equalsIgnoreCase(method)) {
            log.debug(
                    "HTTP request completed method={} path={} status={} durationMs={}",
                    method, path, status, durationMs
            );
        } else {
            log.info(
                    "HTTP request completed method={} path={} status={} durationMs={}",
                    method, path, status, durationMs
            );
        }
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
