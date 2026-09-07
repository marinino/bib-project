package de.marinic.promptlib.llm;

public interface LlmClient {

    /**
     * Runs a single completion request against the underlying model. Implementations must
     * not swallow failures - throw {@link LlmException} so the caller can record a FAILED
     * execution instead of silently returning a bogus result.
     */
    LlmResult complete(LlmRequest request);
}
