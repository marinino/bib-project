package de.marinic.promptlib.prompt;

import de.marinic.promptlib.common.error.NotFoundException;
import de.marinic.promptlib.common.page.PageResponse;
import de.marinic.promptlib.prompt.dto.CreatePromptRequest;
import de.marinic.promptlib.prompt.dto.PromptResponse;
import de.marinic.promptlib.prompt.dto.UpdatePromptRequest;
import de.marinic.promptlib.tag.Tag;
import de.marinic.promptlib.tag.TagRepository;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PromptService {

    private final PromptRepository promptRepository;
    private final TagRepository tagRepository;

    public PromptService(PromptRepository promptRepository, TagRepository tagRepository) {
        this.promptRepository = promptRepository;
        this.tagRepository = tagRepository;
    }

    public PromptResponse create(CreatePromptRequest request, UUID ownerId) {
        Prompt prompt = new Prompt();
        prompt.setTitle(request.title());
        prompt.setDescription(request.description());
        prompt.setCurrentVersionNo(1);
        prompt.setOwnerId(ownerId);
        prompt.setVisibility(request.visibility() != null ? request.visibility() : Visibility.PRIVATE);
        prompt.setTags(resolveTags(request.tags()));

        PromptVersion firstVersion = new PromptVersion();
        firstVersion.setPrompt(prompt);
        firstVersion.setVersionNo(1);
        firstVersion.setContent(request.content());

        prompt.getVersions().add(firstVersion);

        return PromptMapper.toResponse(promptRepository.save(prompt));
    }

    @Transactional(readOnly = true)
    public PromptResponse get(UUID id, UUID requesterId) {
        Prompt prompt = findOrThrow(id);
        PromptAccess.requireReadable(prompt, requesterId);
        return PromptMapper.toResponse(prompt);
    }

    @Transactional(readOnly = true)
    public PageResponse<PromptResponse> search(String query, Set<String> tags, UUID requesterId, Pageable pageable) {
        // Spring Data's Specification.where()/.and() throw on null arguments (no longer a
        // "no-op" like in older versions), so optional filters must be combined manually.
        // fetchTags() always contributes (it has no "off" state), so the combined
        // Specification is never empty here.
        Specification<Prompt> spec =
                Stream.of(
                                PromptSpecifications.titleOrDescriptionContains(query),
                                PromptSpecifications.hasAnyTag(tags),
                                PromptSpecifications.visibleTo(requesterId),
                                PromptSpecifications.fetchTags())
                        .filter(Objects::nonNull)
                        .reduce(Specification::and)
                        .orElseThrow();

        Page<Prompt> page = promptRepository.findAll(spec, pageable);

        return PageResponse.from(page, PromptMapper::toResponse);
    }

    public PromptResponse update(UUID id, UpdatePromptRequest request, UUID requesterId) {
        Prompt prompt = findOrThrow(id);
        PromptAccess.requireOwner(prompt, requesterId);

        if (request.title() != null) {
            prompt.setTitle(request.title());
        }
        if (request.description() != null) {
            prompt.setDescription(request.description());
        }
        if (request.tags() != null) {
            prompt.setTags(resolveTags(request.tags()));
        }
        if (request.visibility() != null) {
            prompt.setVisibility(request.visibility());
        }

        promptRepository.flush();
        return PromptMapper.toResponse(prompt);
    }

    public void delete(UUID id, UUID requesterId) {
        Prompt prompt = findOrThrow(id);
        PromptAccess.requireOwner(prompt, requesterId);
        promptRepository.delete(prompt);
    }

    private Prompt findOrThrow(UUID id) {
        return promptRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Prompt %s not found".formatted(id)));
    }

    private Set<Tag> resolveTags(Set<String> tagNames) {
        if (tagNames == null) {
            return new HashSet<>();
        }

        Set<Tag> tags = new HashSet<>();
        for (String rawName : tagNames) {
            String normalized = rawName.trim().toLowerCase();
            tagRepository.upsertByName(normalized);
            Tag tag =
                    tagRepository
                            .findByName(normalized)
                            .orElseThrow(() -> new IllegalStateException("Tag %s must exist after upsert".formatted(normalized)));
            tags.add(tag);
        }
        return tags;
    }
}
