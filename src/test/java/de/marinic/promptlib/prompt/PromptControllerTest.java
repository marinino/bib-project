package de.marinic.promptlib.prompt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.marinic.promptlib.common.error.NotFoundException;
import de.marinic.promptlib.prompt.dto.PromptResponse;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
}
