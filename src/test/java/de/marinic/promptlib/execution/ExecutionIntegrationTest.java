package de.marinic.promptlib.execution;

import static org.assertj.core.api.Assertions.assertThat;

import de.marinic.promptlib.TestcontainersConfiguration;
import de.marinic.promptlib.execution.dto.CreateExecutionRequest;
import de.marinic.promptlib.execution.dto.ExecutionResponse;
import de.marinic.promptlib.prompt.PromptService;
import de.marinic.promptlib.prompt.dto.CreatePromptRequest;
import de.marinic.promptlib.prompt.dto.PromptResponse;
import de.marinic.promptlib.user.TestUsers;
import de.marinic.promptlib.user.UserRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * End-to-end proof that create() -> AFTER_COMMIT event -> @Async run() -> status update
 * actually works together, against a real database and the real (mock) LLM client - no
 * mocking of our own classes, since the whole point is to verify the wiring between them.
 *
 * <p>Deliberately NOT @Transactional: the async ExecutionRunner thread needs to see the
 * Execution row this test's create() call committed, which an enclosing test transaction
 * (rolled back only at the very end) would hide from it. That means test data here is
 * real and persists past this test's own execution, so it's cleaned up manually in
 * {@link #cleanUp()} instead of relying on rollback - @SpringBootTest classes with
 * identical configuration share one cached ApplicationContext/database for the whole
 * test run, and leftover prompts would otherwise skew total-count assertions in other
 * test classes (e.g. PromptSearchTest).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExecutionIntegrationTest {

    @Autowired private PromptService promptService;
    @Autowired private ExecutionService executionService;
    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final List<UUID> createdPromptIds = new ArrayList<>();
    private UUID userId;

    @BeforeEach
    void resetCircuitBreaker() {
        // Shared singleton state (see ResilienceTest) - a previous test's failures could
        // otherwise leave the breaker OPEN and turn every failure here into
        // CallNotPermittedException instead of the LlmException these tests expect.
        circuitBreakerRegistry.circuitBreaker("llm").reset();
        userId = TestUsers.create(userRepository, passwordEncoder).getId();
    }

    @AfterEach
    void cleanUp() {
        createdPromptIds.forEach(id -> promptService.delete(id, userId));
        createdPromptIds.clear();
    }

    @Test
    void executionEventuallySucceedsAndCarriesTheMockLlmOutput() {
        PromptResponse prompt =
                promptService.create(
                        new CreatePromptRequest("Execution Test", null, "Sag hallo zu {{name}}", Set.of(), null), userId);
        createdPromptIds.add(prompt.id());

        ExecutionResponse created =
                executionService.create(new CreateExecutionRequest(prompt.id(), 1, null, null), userId);
        assertThat(created.status()).isEqualTo("PENDING");

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            ExecutionResponse current = executionService.get(created.id(), userId);
                            assertThat(current.status()).isEqualTo("SUCCEEDED");
                        });

        ExecutionResponse finished = executionService.get(created.id(), userId);
        assertThat(finished.output()).isEqualTo("[mock response to] Sag hallo zu {{name}}");
        assertThat(finished.tokensIn()).isGreaterThan(0);
        assertThat(finished.tokensOut()).isGreaterThan(0);
        assertThat(finished.finishedAt()).isNotNull();
    }

    @Test
    void executionEventuallyFailsAndCarriesTheErrorMessage() {
        PromptResponse prompt =
                promptService.create(
                        new CreatePromptRequest("Execution Failure Test", null, "__FAIL__", Set.of(), null), userId);
        createdPromptIds.add(prompt.id());

        ExecutionResponse created =
                executionService.create(new CreateExecutionRequest(prompt.id(), 1, null, null), userId);

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            ExecutionResponse current = executionService.get(created.id(), userId);
                            assertThat(current.status()).isEqualTo("FAILED");
                        });

        ExecutionResponse finished = executionService.get(created.id(), userId);
        assertThat(finished.errorMessage()).contains("simulated a failure");
        assertThat(finished.finishedAt()).isNotNull();
    }

    // Regression test: an exception that is NOT LlmException (a genuine bug, not an expected
    // LLM failure) must still be caught by ExecutionRunner's outer try/catch and recorded as
    // FAILED - otherwise Spring's default AsyncUncaughtExceptionHandler would only log it and
    // silently drop it, leaving the execution stuck at RUNNING forever with no way for any
    // API consumer to ever find out (confirmed by temporarily narrowing the catch back to
    // LlmException only and re-running this test: it stayed RUNNING with finishedAt == null).
    @Test
    void unexpectedBugIsStillRecordedAsFailed() {
        PromptResponse prompt =
                promptService.create(new CreatePromptRequest("Bug Test", null, "__BUG__", Set.of(), null), userId);
        createdPromptIds.add(prompt.id());

        ExecutionResponse created =
                executionService.create(new CreateExecutionRequest(prompt.id(), 1, null, null), userId);

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            ExecutionResponse current = executionService.get(created.id(), userId);
                            assertThat(current.status()).isEqualTo("FAILED");
                        });

        ExecutionResponse finished = executionService.get(created.id(), userId);
        assertThat(finished.errorMessage()).contains("Simulated unexpected bug");
        assertThat(finished.finishedAt()).isNotNull();
    }
}
