package de.marinic.promptlib.prompt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.marinic.promptlib.common.error.NotFoundException;
import de.marinic.promptlib.prompt.dto.PromptResponse;
import de.marinic.promptlib.prompt.dto.VersionResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PromptVersionController.class)
class PromptVersionControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PromptVersionService promptVersionService;

    @Test
    void createReturnsNewVersion() throws Exception {
        UUID promptId = UUID.randomUUID();
        VersionResponse response = new VersionResponse(2, "neuer Inhalt", null, null, Instant.now());
        given(promptVersionService.create(eq(promptId), any())).willReturn(response);

        mockMvc.perform(
                        post("/api/v1/prompts/{promptId}/versions", promptId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"content":"neuer Inhalt"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(2));
    }

    @Test
    void createForMissingPromptReturns404() throws Exception {
        UUID promptId = UUID.randomUUID();
        given(promptVersionService.create(eq(promptId), any()))
                .willThrow(new NotFoundException("Prompt %s not found".formatted(promptId)));

        mockMvc.perform(
                        post("/api/v1/prompts/{promptId}/versions", promptId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"content":"neuer Inhalt"}
                                        """))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsVersionsDescending() throws Exception {
        UUID promptId = UUID.randomUUID();
        given(promptVersionService.list(promptId))
                .willReturn(
                        List.of(
                                new VersionResponse(2, "v2", null, null, Instant.now()),
                                new VersionResponse(1, "v1", null, null, Instant.now())));

        mockMvc.perform(get("/api/v1/prompts/{promptId}/versions", promptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNo").value(2))
                .andExpect(jsonPath("$[1].versionNo").value(1));
    }

    @Test
    void activateReturnsUpdatedPrompt() throws Exception {
        UUID promptId = UUID.randomUUID();
        Instant now = Instant.now();
        given(promptVersionService.activate(promptId, 2))
                .willReturn(new PromptResponse(promptId, "Test", null, 2, Set.of(), now, now));

        mockMvc.perform(post("/api/v1/prompts/{promptId}/versions/{no}/activate", promptId, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersionNo").value(2));
    }
}
