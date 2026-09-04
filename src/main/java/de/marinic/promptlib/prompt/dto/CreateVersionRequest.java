package de.marinic.promptlib.prompt.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CreateVersionRequest(@NotBlank String content, Map<String, Object> parameters, String notes) {}
