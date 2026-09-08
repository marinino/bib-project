package de.marinic.promptlib.prompt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.marinic.promptlib.common.error.NotFoundException;
import de.marinic.promptlib.common.security.JwtService;
import de.marinic.promptlib.common.security.ProblemDetailSecurityHandlers;
import de.marinic.promptlib.common.security.SecurityConfig;
import de.marinic.promptlib.common.security.TestPrincipals;
import de.marinic.promptlib.common.security.UserDetailsServiceImpl;
import de.marinic.promptlib.prompt.dto.PromptResponse;
import de.marinic.promptlib.prompt.dto.VersionResponse;
import de.marinic.promptlib.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// See PromptControllerTest for why SecurityConfig must be imported and the real filter chain
// left enabled here.
@WebMvcTest(PromptVersionController.class)
@Import({SecurityConfig.class, ProblemDetailSecurityHandlers.class})
class PromptVersionControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PromptVersionService promptVersionService;

    // See PromptControllerTest for why these unused mocks are needed here.
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private UserDetailsServiceImpl userDetailsService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void createReturnsNewVersion() throws Exception {
        UUID promptId = UUID.randomUUID();
        VersionResponse response = new VersionResponse(2, "neuer Inhalt", null, null, Instant.now());
        given(promptVersionService.create(eq(promptId), any(), eq(userId))).willReturn(response);

        mockMvc.perform(
                        post("/api/v1/prompts/{promptId}/versions", promptId)
                                .with(TestPrincipals.user(userId))
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
        given(promptVersionService.create(eq(promptId), any(), eq(userId)))
                .willThrow(new NotFoundException("Prompt %s not found".formatted(promptId)));

        mockMvc.perform(
                        post("/api/v1/prompts/{promptId}/versions", promptId)
                                .with(TestPrincipals.user(userId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"content":"neuer Inhalt"}
                                        """))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsVersionsDescending() throws Exception {
        UUID promptId = UUID.randomUUID();
        given(promptVersionService.list(promptId, userId))
                .willReturn(
                        List.of(
                                new VersionResponse(2, "v2", null, null, Instant.now()),
                                new VersionResponse(1, "v1", null, null, Instant.now())));

        mockMvc.perform(get("/api/v1/prompts/{promptId}/versions", promptId).with(TestPrincipals.user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNo").value(2))
                .andExpect(jsonPath("$[1].versionNo").value(1));
    }

    @Test
    void activateReturnsUpdatedPrompt() throws Exception {
        UUID promptId = UUID.randomUUID();
        Instant now = Instant.now();
        given(promptVersionService.activate(promptId, 2, userId))
                .willReturn(
                        new PromptResponse(
                                promptId, "Test", null, 2, Set.of(), userId, Visibility.PRIVATE, now, now));

        mockMvc.perform(
                        post("/api/v1/prompts/{promptId}/versions/{no}/activate", promptId, 2)
                                .with(TestPrincipals.user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersionNo").value(2));
    }
}
