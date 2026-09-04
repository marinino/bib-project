package de.marinic.promptlib.prompt;

import de.marinic.promptlib.prompt.dto.PromptResponse;
import de.marinic.promptlib.tag.Tag;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class PromptMapper {

    private PromptMapper() {}

    public static PromptResponse toResponse(Prompt prompt) {
        Set<String> tagNames =
                prompt.getTags().stream().map(Tag::getName).collect(Collectors.toCollection(TreeSet::new));

        return new PromptResponse(
                prompt.getId(),
                prompt.getTitle(),
                prompt.getDescription(),
                prompt.getCurrentVersionNo(),
                tagNames,
                prompt.getCreatedAt(),
                prompt.getUpdatedAt());
    }
}
