package com.ratemyuj.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openrouter")
public record OpenRouterProperties(
        String apiKey,
        String baseUrl,          // https://openrouter.ai/api/v1
        String model,            // e.g. "openai/gpt-4o-mini" or "anthropic/claude-haiku-4.5"
        String fallbackModel,    // cheaper/alternate model if the primary errors
        int timeoutSeconds,      // hard cap on the sync moderation call
        double escalateBelowConfidence  // verdicts under this confidence go to MANUAL_REVIEW
) {}
