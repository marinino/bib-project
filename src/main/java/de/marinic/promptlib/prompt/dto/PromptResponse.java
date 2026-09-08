package de.marinic.promptlib.prompt.dto;

import de.marinic.promptlib.prompt.Visibility;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record PromptResponse(
        UUID id,
        String title,
        String description,
        Integer currentVersionNo,
        Set<String> tags,
        UUID ownerId,
        Visibility visibility,
        Instant createdAt,
        Instant updatedAt) {}
