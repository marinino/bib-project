package de.marinic.promptlib.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import de.marinic.promptlib.TestcontainersConfiguration;
import de.marinic.promptlib.prompt.dto.CreatePromptRequest;
import de.marinic.promptlib.tag.Tag;
import de.marinic.promptlib.tag.TagRepository;
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

/**
 * Regression test for the race condition in {@code PromptService.resolveTags()}: creating
 * a brand-new tag used to be a plain find-or-insert, so two concurrent requests both
 * creating the same not-yet-existing tag could both attempt the INSERT and one would fail
 * with a unique constraint violation. Fixed via TagRepository.upsertByName() (INSERT ...
 * ON CONFLICT DO NOTHING, which never throws).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PromptTagConcurrencyTest {

    @Autowired private PromptService promptService;
    @Autowired private TagRepository tagRepository;

    @Test
    void concurrentPromptsWithTheSameNewTagDoNotFailAndShareOneTagRow() throws Exception {
        int concurrentRequests = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);

        List<Callable<Void>> tasks =
                IntStream.range(0, concurrentRequests)
                        .<Callable<Void>>mapToObj(
                                i ->
                                        () -> {
                                            promptService.create(
                                                    new CreatePromptRequest(
                                                            "Concurrent Tag Prompt " + i,
                                                            null,
                                                            "content",
                                                            Set.of("brand-new-tag")));
                                            return null;
                                        })
                        .toList();

        List<Future<Void>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        // Surface any exception a task swallowed (invokeAll doesn't propagate them itself).
        for (Future<Void> future : futures) {
            future.get();
        }

        List<Tag> matchingTags =
                tagRepository.findAll().stream().filter(t -> t.getName().equals("brand-new-tag")).toList();
        assertThat(matchingTags).hasSize(1);
    }
}
