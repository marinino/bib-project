package de.marinic.promptlib.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import de.marinic.promptlib.TestcontainersConfiguration;
import de.marinic.promptlib.prompt.dto.CreatePromptRequest;
import de.marinic.promptlib.prompt.dto.CreateVersionRequest;
import de.marinic.promptlib.prompt.dto.PromptResponse;
import de.marinic.promptlib.prompt.dto.VersionResponse;
import de.marinic.promptlib.user.TestUsers;
import de.marinic.promptlib.user.UserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PromptVersionConcurrencyTest {

    @Autowired private PromptService promptService;
    @Autowired private PromptVersionService promptVersionService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void concurrentVersionCreationProducesUniqueSequentialNumbers() throws Exception {
        UUID userId = TestUsers.create(userRepository, passwordEncoder).getId();
        PromptResponse prompt =
                promptService.create(new CreatePromptRequest("Concurrency Test", null, "v1", Set.of(), null), userId);

        int concurrentRequests = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);

        List<Callable<VersionResponse>> tasks =
                IntStream.range(0, concurrentRequests)
                        .<Callable<VersionResponse>>mapToObj(
                                i ->
                                        () ->
                                                promptVersionService.create(
                                                        prompt.id(),
                                                        new CreateVersionRequest("content " + i, null, null),
                                                        userId))
                        .toList();

        List<Future<VersionResponse>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        List<Integer> versionNumbers = new ArrayList<>();
        for (Future<VersionResponse> future : futures) {
            versionNumbers.add(future.get().versionNo());
        }

        // version 1 already exists from prompt creation, so the concurrent requests
        // must land on 2..11 with no duplicates and no gaps despite running in parallel.
        assertThat(new HashSet<>(versionNumbers)).hasSize(concurrentRequests);
        assertThat(versionNumbers)
                .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(2, concurrentRequests + 1).boxed().toList());
    }
}
