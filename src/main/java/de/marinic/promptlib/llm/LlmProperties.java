package de.marinic.promptlib.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "promptlib.llm")
public record LlmProperties(
        String baseUrl, String apiKey, String defaultModel, int connectTimeoutMs, int readTimeoutMs) {}
