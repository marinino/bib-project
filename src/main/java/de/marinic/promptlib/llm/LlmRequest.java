package de.marinic.promptlib.llm;

import java.util.Map;

public record LlmRequest(String model, String prompt, Map<String, Object> parameters) {}
