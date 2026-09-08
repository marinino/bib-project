package de.marinic.promptlib.prompt.dto;

import de.marinic.promptlib.prompt.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreatePromptRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        @NotBlank String content,
        Set<String> tags,
        Visibility visibility) {}
