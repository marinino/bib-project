package de.marinic.promptlib.prompt.dto;

import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdatePromptRequest(
        @Size(max = 200) String title, String description, Set<String> tags) {}
