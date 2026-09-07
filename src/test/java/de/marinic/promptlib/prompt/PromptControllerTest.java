package de.marinic.promptlib.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.marinic.promptlib.common.error.NotFoundException;
import de.marinic.promptlib.common.page.PageResponse;
import de.marinic.promptlib.prompt.dto.PromptResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PromptController.class)
class PromptControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PromptService promptService;

    @Test
    void createReturns201WithLocationHeader() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        PromptResponse response =
                new PromptResponse(id, "Test", "desc", 1, Set.of("cv"), now, now);
        given(promptService.create(any())).willReturn(response);

        mockMvc.perform(
                        post("/api/v1/prompts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"title":"Test","description":"desc","content":"Hello","tags":["cv"]}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/prompts/" + id))
                .andExpect(jsonPath("$.title").value("Test"));
    }

    @Test
    void createWithBlankTitleReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(
                        post("/api/v1/prompts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"title":"","content":""}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void createWithMalformedJsonReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/prompts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getReturns200ForExistingPrompt() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        given(promptService.get(eq(id)))
                .willReturn(new PromptResponse(id, "Test", null, 1, Set.of(), now, now));

        mockMvc.perform(get("/api/v1/prompts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getReturns404ForMissingPrompt() throws Exception {
        UUID id = UUID.randomUUID();
        given(promptService.get(eq(id))).willThrow(new NotFoundException("Prompt %s not found".formatted(id)));

        mockMvc.perform(get("/api/v1/prompts/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/prompts/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchBindsQueryParamsAndReturnsPageResponse() throws Exception {
        Instant now = Instant.now();
        PromptResponse prompt = new PromptResponse(UUID.randomUUID(), "Video", null, 1, Set.of("cv"), now, now);
        PageResponse<PromptResponse> page = new PageResponse<>(List.of(prompt), 0, 10, 1, 1);
        given(promptService.search(any(), any(), any())).willReturn(page);

        mockMvc.perform(
                        get("/api/v1/prompts")
                                .param("query", "video")
                                .param("tags", "cv,comfyui")
                                .param("page", "0")
                                .param("size", "10")
                                .param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Video"))
                .andExpect(jsonPath("$.totalElements").value(1));

        ArgumentCaptor<Set<String>> tagsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(promptService).search(eq("video"), tagsCaptor.capture(), any());
        assertThat(tagsCaptor.getValue()).containsExactlyInAnyOrder("cv", "comfyui");
    }
}
