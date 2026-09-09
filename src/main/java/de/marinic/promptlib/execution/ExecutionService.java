package de.marinic.promptlib.execution;

import de.marinic.promptlib.common.error.NotFoundException;
import de.marinic.promptlib.execution.dto.CreateExecutionRequest;
import de.marinic.promptlib.execution.dto.ExecutionResponse;
import de.marinic.promptlib.prompt.Prompt;
import de.marinic.promptlib.prompt.PromptAccess;
import de.marinic.promptlib.prompt.PromptRepository;
import de.marinic.promptlib.prompt.PromptVersion;
import de.marinic.promptlib.prompt.PromptVersionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final PromptVersionRepository promptVersionRepository;
    private final PromptRepository promptRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ExecutionService(
            ExecutionRepository executionRepository,
            PromptVersionRepository promptVersionRepository,
            PromptRepository promptRepository,
            ApplicationEventPublisher eventPublisher) {
        this.executionRepository = executionRepository;
        this.promptVersionRepository = promptVersionRepository;
        this.promptRepository = promptRepository;
        this.eventPublisher = eventPublisher;
    }

    public ExecutionResponse create(CreateExecutionRequest request, UUID requesterId) {
        PromptVersion version =
                promptVersionRepository
                        .findByPromptIdAndVersionNo(request.promptId(), request.versionNo())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "Version %d of prompt %s not found"
                                                        .formatted(request.versionNo(), request.promptId())));
        // Lazy proxy, safe to touch here - we're inside this method's own transaction.
        PromptAccess.requireReadable(version.getPrompt(), requesterId);

        Execution execution = new Execution();
        execution.setPromptVersion(version);
        execution.setStatus(ExecutionStatus.PENDING);
        execution.setModel(request.model());
        execution.setInputParams(request.inputParams());

        Execution saved = executionRepository.save(execution);

        // Fired only after this transaction commits - see ExecutionCreatedListener.
        eventPublisher.publishEvent(new ExecutionCreatedEvent(saved.getId()));

        return ExecutionMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ExecutionResponse get(UUID id, UUID requesterId) {
        Execution execution = findOrThrow(id);
        // Lazy proxies, safe to touch here - same reasoning as create(): still inside this
        // method's own transaction.
        PromptAccess.requireReadable(execution.getPromptVersion().getPrompt(), requesterId);
        return ExecutionMapper.toResponse(execution);
    }

    @Transactional(readOnly = true)
    public List<ExecutionResponse> listByPrompt(UUID promptId, UUID requesterId) {
        Prompt prompt =
                promptRepository
                        .findById(promptId)
                        .orElseThrow(() -> new NotFoundException("Prompt %s not found".formatted(promptId)));
        PromptAccess.requireReadable(prompt, requesterId);
        return executionRepository.findByPromptVersion_Prompt_IdOrderByCreatedAtDesc(promptId).stream()
                .map(ExecutionMapper::toResponse)
                .toList();
    }

    private Execution findOrThrow(UUID id) {
        return executionRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Execution %s not found".formatted(id)));
    }
}
