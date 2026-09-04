package de.marinic.promptlib.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import de.marinic.promptlib.TestcontainersConfiguration;
import de.marinic.promptlib.prompt.dto.CreatePromptRequest;
import de.marinic.promptlib.prompt.dto.CreateVersionRequest;
import de.marinic.promptlib.prompt.dto.PromptResponse;
import de.marinic.promptlib.prompt.dto.VersionResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PromptVersionConcurrencyTest {

    @Autowired private PromptService promptService;
    @Autowired private PromptVersionService promptVersionService;

    @Test
    void concurrentVersionCreationProducesUniqueSequentialNumbers() throws Exception {
        PromptResponse prompt =
                promptService.create(new CreatePromptRequest("Concurrency Test", null, "v1", Set.of()));

        int concurrentRequests = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);

        List<Callable<VersionResponse>> tasks =
                IntStream.range(0, concurrentRequests)
                        .<Callable<VersionResponse>>mapToObj(
                                i ->
                                        () ->
                                                promptVersionService.create(
                                                        prompt.id(),
                                                        new CreateVersionRequest("content " + i, null, null)))
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
