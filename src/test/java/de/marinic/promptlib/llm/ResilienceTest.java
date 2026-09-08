package de.marinic.promptlib.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.marinic.promptlib.TestcontainersConfiguration;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves the actual (not assumed) interaction between @Retry and @CircuitBreaker on
 * LlmClient.complete(), configured with max-attempts=3, sliding-window-size=4,
 * minimum-number-of-calls=4, failure-rate-threshold=50%: Retry wraps CircuitBreaker (Spring
 * Boot's default aspect order), so every retry attempt is itself a circuit breaker call.
 *
 * <p>The "llm" CircuitBreaker is a singleton with real, persistent state (CLOSED/OPEN/...)
 * shared across the whole test run - @SpringBootTest classes with identical configuration
 * cache and reuse one ApplicationContext. Without an explicit reset, whichever test happens
 * to run first (order is not guaranteed) leaves the breaker OPEN for everyone after it.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ResilienceTest {

    @Autowired private MockLlmClient mockLlmClient;
    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetResilienceState() {
        circuitBreakerRegistry.circuitBreaker("llm").reset();
        mockLlmClient.resetCallCount();
    }

    @Test
    void repeatedFailuresRetryThenTripTheCircuitBreaker() {
        LlmRequest failingRequest = new LlmRequest(null, "__FAIL__", null);

        // Call 1: all 3 retry attempts run and fail (window not yet full: 3/4).
        assertThatThrownBy(() -> mockLlmClient.complete(failingRequest)).isInstanceOf(LlmException.class);
        assertThat(mockLlmClient.callCount()).isEqualTo(3);

        // Call 2: first retry attempt is the 4th recorded failure - window full, 100%
        // failure rate trips the breaker OPEN immediately; the remaining retry attempts
        // for THIS call are skipped because CallNotPermittedException isn't a retryable
        // exception in our config, so it propagates straight through.
        assertThatThrownBy(() -> mockLlmClient.complete(failingRequest))
                .isInstanceOf(CallNotPermittedException.class);
        assertThat(mockLlmClient.callCount()).isEqualTo(4);

        // Call 3: breaker still OPEN (wait-duration-in-open-state not elapsed) - fails
        // fast without ever reaching the mock at all.
        assertThatThrownBy(() -> mockLlmClient.complete(failingRequest))
                .isInstanceOf(CallNotPermittedException.class);
        assertThat(mockLlmClient.callCount()).isEqualTo(4);
    }
}
