package de.marinic.promptlib.execution.dto;

import java.time.Instant;
import java.util.UUID;

public record ExecutionResponse(
        UUID id,
        UUID promptId,
        int versionNo,
        String status,
        String model,
        String output,
        Integer latencyMs,
        Integer tokensIn,
        Integer tokensOut,
        String errorMessage,
        Instant createdAt,
        Instant finishedAt) {}
