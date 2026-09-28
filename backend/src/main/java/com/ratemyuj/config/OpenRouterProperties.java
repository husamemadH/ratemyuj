package com.ratemyuj.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openrouter")
public record OpenRouterProperties(
        String apiKey,
        String baseUrl,          // https://openrouter.ai
        String decisionsPath,    // /api/alpha/decisions — the Jev (TypeSafe) endpoint
        String model,            // e.g. "typesafe/jev-1.13" or the "~typesafe/jev-latest" alias
        int timeoutSeconds,      // hard cap on the sync moderation call
        double escalateBelowConfidence,  // verdicts under this confidence go to MANUAL_REVIEW
        double categoryThreshold         // noul probability at which a policy category is flagged
) {}
