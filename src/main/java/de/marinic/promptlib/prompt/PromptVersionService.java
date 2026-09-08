package de.marinic.promptlib.prompt;

import de.marinic.promptlib.common.error.NotFoundException;
import de.marinic.promptlib.prompt.dto.CreateVersionRequest;
import de.marinic.promptlib.prompt.dto.PromptResponse;
import de.marinic.promptlib.prompt.dto.VersionResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PromptVersionService {

    private final PromptRepository promptRepository;
    private final PromptVersionRepository promptVersionRepository;

    public PromptVersionService(PromptRepository promptRepository, PromptVersionRepository promptVersionRepository) {
        this.promptRepository = promptRepository;
        this.promptVersionRepository = promptVersionRepository;
    }

    public VersionResponse create(UUID promptId, CreateVersionRequest request, UUID requesterId) {
        // Pessimistic lock: serializes concurrent "next version number" calculations for
        // the same prompt so two parallel requests can't compute the same version_no.
        Prompt prompt = findPromptForUpdateOrThrow(promptId);
        PromptAccess.requireOwner(prompt, requesterId);

        int nextVersionNo = promptVersionRepository.findMaxVersionNo(promptId) + 1;

        PromptVersion version = new PromptVersion();
        version.setPrompt(prompt);
        version.setVersionNo(nextVersionNo);
        version.setContent(request.content());
        version.setParameters(request.parameters());
        version.setNotes(request.notes());

        return PromptMapper.toVersionResponse(promptVersionRepository.save(version));
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> list(UUID promptId, UUID requesterId) {
        Prompt prompt =
                promptRepository
                        .findById(promptId)
                        .orElseThrow(() -> new NotFoundException("Prompt %s not found".formatted(promptId)));
        PromptAccess.requireReadable(prompt, requesterId);
        return promptVersionRepository.findByPromptIdOrderByVersionNoDesc(promptId).stream()
                .map(PromptMapper::toVersionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VersionResponse get(UUID promptId, int versionNo, UUID requesterId) {
        Prompt prompt =
                promptRepository
                        .findById(promptId)
                        .orElseThrow(() -> new NotFoundException("Prompt %s not found".formatted(promptId)));
        PromptAccess.requireReadable(prompt, requesterId);
        return PromptMapper.toVersionResponse(findVersionOrThrow(promptId, versionNo));
    }

    public PromptResponse activate(UUID promptId, int versionNo, UUID requesterId) {
        Prompt prompt = findPromptForUpdateOrThrow(promptId);
        PromptAccess.requireOwner(prompt, requesterId);
        findVersionOrThrow(promptId, versionNo);

        prompt.setCurrentVersionNo(versionNo);

        promptRepository.flush();
        return PromptMapper.toResponse(prompt);
    }

    private Prompt findPromptForUpdateOrThrow(UUID promptId) {
        return promptRepository
                .findByIdForUpdate(promptId)
                .orElseThrow(() -> new NotFoundException("Prompt %s not found".formatted(promptId)));
    }

    private PromptVersion findVersionOrThrow(UUID promptId, int versionNo) {
        return promptVersionRepository
                .findByPromptIdAndVersionNo(promptId, versionNo)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        "Version %d of prompt %s not found".formatted(versionNo, promptId)));
    }
}
