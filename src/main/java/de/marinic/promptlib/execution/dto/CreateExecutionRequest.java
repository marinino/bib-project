package de.marinic.promptlib.execution.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record CreateExecutionRequest(
        @NotNull UUID promptId, @NotNull Integer versionNo, String model, Map<String, Object> inputParams) {}
