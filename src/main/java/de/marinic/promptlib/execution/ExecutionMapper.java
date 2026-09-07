package de.marinic.promptlib.execution;

import de.marinic.promptlib.execution.dto.ExecutionResponse;

public final class ExecutionMapper {

    private ExecutionMapper() {}

    public static ExecutionResponse toResponse(Execution execution) {
        return new ExecutionResponse(
                execution.getId(),
                // Only called from ExecutionService, always inside an active transaction,
                // so navigating the lazy promptVersion -> prompt chain is safe here.
                execution.getPromptVersion().getPrompt().getId(),
                execution.getPromptVersion().getVersionNo(),
                execution.getStatus().name(),
                execution.getModel(),
                execution.getOutput(),
                execution.getLatencyMs(),
                execution.getTokensIn(),
                execution.getTokensOut(),
                execution.getErrorMessage(),
                execution.getCreatedAt(),
                execution.getFinishedAt());
    }
}
