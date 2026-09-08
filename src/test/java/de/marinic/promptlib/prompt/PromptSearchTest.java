package de.marinic.promptlib.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import de.marinic.promptlib.TestcontainersConfiguration;
import de.marinic.promptlib.common.page.PageResponse;
import de.marinic.promptlib.prompt.dto.CreatePromptRequest;
import de.marinic.promptlib.prompt.dto.PromptResponse;
import de.marinic.promptlib.user.TestUsers;
import de.marinic.promptlib.user.UserRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

// Rolls back after each test method (Spring's test transaction support), because
// @SpringBootTest classes share one cached ApplicationContext/database across the whole
// test run and otherwise accumulate prompts across test methods and test classes.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PromptSearchTest {

    @Autowired private PromptService promptService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID userId;

    @BeforeEach
    void seedPrompts() {
        userId = TestUsers.create(userRepository, passwordEncoder).getId();
        promptService.create(
                new CreatePromptRequest("Video Generator", "Für ComfyUI", "content a", Set.of("comfyui", "video"), null),
                userId);
        promptService.create(
                new CreatePromptRequest("Image Upscaler", "Für Bildbearbeitung", "content b", Set.of("cv"), null),
                userId);
        promptService.create(
                new CreatePromptRequest("Video Captioner", "Beschreibt Videos", "content c", Set.of("video", "nlp"), null),
                userId);
    }

    @Test
    void filtersByQueryInTitle() {
        PageResponse<PromptResponse> result =
                promptService.search("video", null, userId, PageRequest.of(0, 20, Sort.by("title")));

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).extracting(PromptResponse::title).containsExactlyInAnyOrder(
                "Video Generator", "Video Captioner");
    }

    @Test
    void filtersByTag() {
        PageResponse<PromptResponse> result =
                promptService.search(null, Set.of("cv"), userId, PageRequest.of(0, 20, Sort.by("title")));

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().getFirst().title()).isEqualTo("Image Upscaler");
    }

    @Test
    void combinesQueryAndTagFilter() {
        PageResponse<PromptResponse> result =
                promptService.search("video", Set.of("nlp"), userId, PageRequest.of(0, 20, Sort.by("title")));

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().getFirst().title()).isEqualTo("Video Captioner");
    }

    @Test
    void paginatesAndSortsByTitleAscending() {
        PageResponse<PromptResponse> firstPage =
                promptService.search(null, null, userId, PageRequest.of(0, 2, Sort.by("title").ascending()));

        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.content()).extracting(PromptResponse::title)
                .containsExactly("Image Upscaler", "Video Captioner");

        PageResponse<PromptResponse> secondPage =
                promptService.search(null, null, userId, PageRequest.of(1, 2, Sort.by("title").ascending()));
        assertThat(secondPage.content()).extracting(PromptResponse::title).containsExactly("Video Generator");
    }

    @Test
    void returnsAllWhenNoFilterGiven() {
        PageResponse<PromptResponse> result = promptService.search(null, null, userId, PageRequest.of(0, 20));
        assertThat(result.totalElements()).isEqualTo(3);
    }

    @Test
    void tagFilterWithMultipleTagsActsAsOr() {
        PageResponse<PromptResponse> result =
                promptService.search(null, Set.of("cv", "nlp"), userId, PageRequest.of(0, 20, Sort.by("title")));

        assertThat(result.content()).extracting(PromptResponse::title)
                .containsExactlyInAnyOrder("Image Upscaler", "Video Captioner");
    }
}
