package de.marinic.promptlib.llm;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Default LlmClient: no network, no cost, deterministic - so the rest of the app (and its
 * tests) can run without a real API key. Active unless the "real" profile is set.
 *
 * <p>Recognizes magic markers in the prompt text so tests can deterministically exercise
 * failure/latency/resilience handling without relying on a flaky real API:
 *
 * <ul>
 *   <li>{@code __FAIL__} - throws {@link LlmException}
 *   <li>{@code __BUG__} - throws a plain {@link IllegalStateException}, i.e. NOT an
 *       LlmException - simulates a genuine bug rather than an expected LLM failure, to
 *       exercise ExecutionRunner's catch-all safety net
 *   <li>{@code __SLOW__} - sleeps briefly before responding
 * </ul>
 *
 * <p>{@link #callCount} lets tests observe how many times the (retried/circuit-breaker-
 * wrapped) method actually ran underneath, not just how many times the caller called it.
 */
@Component
@Profile("!real")
public class MockLlmClient implements LlmClient {

    private final AtomicInteger callCount = new AtomicInteger();

    public int callCount() {
        return callCount.get();
    }

    public void resetCallCount() {
        callCount.set(0);
    }

    @Override
    @Retry(name = "llm")
    @CircuitBreaker(name = "llm")
    public LlmResult complete(LlmRequest request) {
        callCount.incrementAndGet();
        String prompt = request.prompt();

        if (prompt != null && prompt.contains("__FAIL__")) {
            throw new LlmException("Mock LLM client simulated a failure");
        }

        if (prompt != null && prompt.contains("__BUG__")) {
            throw new IllegalStateException("Simulated unexpected bug, not an LlmException");
        }

        if (prompt != null && prompt.contains("__SLOW__")) {
            sleep();
        }

        String output = "[mock response to] " + prompt;
        int tokensIn = wordCount(prompt);
        int tokensOut = wordCount(output);

        return new LlmResult(output, tokensIn, tokensOut, 5);
    }

    private static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private static void sleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while simulating latency", e);
        }
    }
}
