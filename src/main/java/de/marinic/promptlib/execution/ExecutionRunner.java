package de.marinic.promptlib.execution;

import de.marinic.promptlib.llm.LlmClient;
import de.marinic.promptlib.llm.LlmException;
import de.marinic.promptlib.llm.LlmRequest;
import de.marinic.promptlib.llm.LlmResult;
import de.marinic.promptlib.prompt.PromptVersion;
import de.marinic.promptlib.prompt.PromptVersionRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs the actual LLM call outside of any request transaction. Each repository call here
 * (find/save) is its own short, independent transaction (Spring Data's default per-method
 * transaction demarcation) - the LLM call itself, which can take seconds, never happens
 * while a database transaction is held open.
 *
 * <p>A separate bean (not a method on {@link ExecutionService}) on purpose: {@code @Async}
 * only works through Spring's proxy, and calling an {@code @Async} method on {@code this}
 * from within the same class would run it synchronously on the caller's thread instead.
 */
@Component
public class ExecutionRunner {

    private static final Logger log = LoggerFactory.getLogger(ExecutionRunner.class);

    private final ExecutionRepository executionRepository;
    private final PromptVersionRepository promptVersionRepository;
    private final LlmClient llmClient;

    public ExecutionRunner(
            ExecutionRepository executionRepository,
            PromptVersionRepository promptVersionRepository,
            LlmClient llmClient) {
        this.executionRepository = executionRepository;
        this.promptVersionRepository = promptVersionRepository;
        this.llmClient = llmClient;
    }

    @Async("executionTaskExecutor")
    public void run(UUID executionId) {
        Execution execution = executionRepository.findById(executionId).orElse(null);
        if (execution == null) {
            log.warn("Execution {} vanished before it could run", executionId);
            return;
        }

        // Safe even outside a session: reading only the id of a lazy association never
        // triggers a DB hit, Hibernate already knows it from the FK column.
        UUID promptVersionId = execution.getPromptVersion().getId();

        execution.setStatus(ExecutionStatus.RUNNING);
        executionRepository.save(execution);

        PromptVersion version = promptVersionRepository.findById(promptVersionId).orElse(null);
        if (version == null) {
            recordFailure(executionId, "Prompt version %s no longer exists".formatted(promptVersionId));
            return;
        }

        LlmResult result;
        try {
            result = llmClient.complete(new LlmRequest(execution.getModel(), version.getContent(), execution.getInputParams()));
        } catch (LlmException e) {
            recordFailure(executionId, e.getMessage());
            return;
        }

        recordSuccess(executionId, result);
    }

    private void recordSuccess(UUID executionId, LlmResult result) {
        Execution execution = executionRepository.findById(executionId).orElseThrow();
        execution.setStatus(ExecutionStatus.SUCCEEDED);
        execution.setOutput(result.output());
        execution.setTokensIn(result.tokensIn());
        execution.setTokensOut(result.tokensOut());
        execution.setLatencyMs((int) result.latencyMs());
        execution.setFinishedAt(Instant.now());
        executionRepository.save(execution);
    }

    private void recordFailure(UUID executionId, String message) {
        Execution execution = executionRepository.findById(executionId).orElseThrow();
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setErrorMessage(message);
        execution.setFinishedAt(Instant.now());
        executionRepository.save(execution);
    }
}
