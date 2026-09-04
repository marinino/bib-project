package de.marinic.promptlib.prompt;

import de.marinic.promptlib.common.error.NotFoundException;
import de.marinic.promptlib.prompt.dto.CreatePromptRequest;
import de.marinic.promptlib.prompt.dto.PromptResponse;
import de.marinic.promptlib.prompt.dto.UpdatePromptRequest;
import de.marinic.promptlib.tag.Tag;
import de.marinic.promptlib.tag.TagRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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

    public PromptResponse create(CreatePromptRequest request) {
        Prompt prompt = new Prompt();
        prompt.setTitle(request.title());
        prompt.setDescription(request.description());
        prompt.setCurrentVersionNo(1);
        prompt.setTags(resolveTags(request.tags()));

        PromptVersion firstVersion = new PromptVersion();
        firstVersion.setPrompt(prompt);
        firstVersion.setVersionNo(1);
        firstVersion.setContent(request.content());

        prompt.getVersions().add(firstVersion);

        return PromptMapper.toResponse(promptRepository.save(prompt));
    }

    @Transactional(readOnly = true)
    public PromptResponse get(UUID id) {
        return PromptMapper.toResponse(findOrThrow(id));
    }

    public PromptResponse update(UUID id, UpdatePromptRequest request) {
        Prompt prompt = findOrThrow(id);

        if (request.title() != null) {
            prompt.setTitle(request.title());
        }
        if (request.description() != null) {
            prompt.setDescription(request.description());
        }
        if (request.tags() != null) {
            prompt.setTags(resolveTags(request.tags()));
        }

        promptRepository.flush();
        return PromptMapper.toResponse(prompt);
    }

    public void delete(UUID id) {
        promptRepository.delete(findOrThrow(id));
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
            Tag tag = tagRepository.findByName(normalized).orElseGet(() -> tagRepository.save(new Tag(normalized)));
            tags.add(tag);
        }
        return tags;
    }
}
