package de.marinic.promptlib.llm;

public record LlmResult(String output, int tokensIn, int tokensOut, long latencyMs) {}
