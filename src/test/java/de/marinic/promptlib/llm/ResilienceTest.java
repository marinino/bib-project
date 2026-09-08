package de.marinic.promptlib.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.marinic.promptlib.TestcontainersConfiguration;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
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

    // Regression test for a real bug found by actually waiting past wait-duration-in-open-
    // state and observing the breaker stay OPEN forever: automaticTransitionFromOpenTo-
    // HalfOpenEnabled defaults to false, meaning the breaker never re-checks on its own once
    // OPEN - it would reject every execution permanently, even long after the LLM recovers,
    // unless this is explicitly enabled (see application.properties).
    @Test
    void circuitBreakerSelfHealsAfterWaitDuration() throws InterruptedException {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("llm");
        LlmRequest failingRequest = new LlmRequest(null, "__FAIL__", null);

        // Trip the breaker.
        assertThatThrownBy(() -> mockLlmClient.complete(failingRequest)).isInstanceOf(LlmException.class);
        assertThatThrownBy(() -> mockLlmClient.complete(failingRequest))
                .isInstanceOf(CallNotPermittedException.class);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Past wait-duration-in-open-state (2s): with automatic-transition enabled, a
        // background task flips it to HALF_OPEN on its own, without needing a call.
        Thread.sleep(3000);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // The mock is still failing (still __FAIL__), so the permitted trial calls in
        // HALF_OPEN also fail, and the breaker correctly snaps back to OPEN.
        assertThatThrownBy(() -> mockLlmClient.complete(failingRequest))
                .isInstanceOfAny(LlmException.class, CallNotPermittedException.class);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
