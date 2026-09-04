package de.marinic.promptlib.prompt.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record PromptResponse(
        UUID id,
        String title,
        String description,
        Integer currentVersionNo,
        Set<String> tags,
        Instant createdAt,
        Instant updatedAt) {}
