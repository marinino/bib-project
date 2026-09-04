package de.marinic.promptlib.prompt.dto;

import java.time.Instant;
import java.util.Map;

public record VersionResponse(
        int versionNo, String content, Map<String, Object> parameters, String notes, Instant createdAt) {}
