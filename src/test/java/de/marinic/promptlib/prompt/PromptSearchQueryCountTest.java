package de.marinic.promptlib.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import de.marinic.promptlib.TestcontainersConfiguration;
import de.marinic.promptlib.common.page.PageResponse;
import de.marinic.promptlib.prompt.dto.CreatePromptRequest;
import de.marinic.promptlib.prompt.dto.PromptResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regression test for the N+1 fix in {@link PromptSpecifications#fetchTags()}: searching a
 * page of prompts must not issue one extra SELECT per prompt to lazy-load its tags.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PromptSearchQueryCountTest {

    @Autowired private PromptService promptService;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private EntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        for (int i = 0; i < 5; i++) {
            promptService.create(
                    new CreatePromptRequest("Prompt " + i, null, "content " + i, Set.of("tag-a", "tag-b")));
        }

        // Detach everything from the persistence context: without this, the Prompt
        // objects created above stay "managed" (Hibernate's first-level cache), and
        // search() below would get served the same in-memory objects - whose "tags"
        // are already populated - instead of genuinely re-querying, hiding the N+1.
        entityManager.flush();
        entityManager.clear();

        statistics.clear();
    }

    @Test
    void searchingFivePromptsWithTagsExecutesOnlyTwoQueries() {
        PageResponse<PromptResponse> page = promptService.search(null, null, PageRequest.of(0, 5));

        assertThat(page.content()).hasSize(5);
        assertThat(page.content()).allSatisfy(p -> assertThat(p.tags()).containsExactly("tag-a", "tag-b"));

        // 1 content query (page, tags fetched in the same query) + 1 count query.
        // Before fetchTags(), this was 2 + N: one extra SELECT per prompt to lazily
        // load its tags collection during PromptMapper.toResponse().
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }
}
