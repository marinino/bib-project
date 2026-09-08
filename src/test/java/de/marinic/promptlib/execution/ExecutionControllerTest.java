package de.marinic.promptlib.execution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.marinic.promptlib.common.error.NotFoundException;
import de.marinic.promptlib.common.security.JwtService;
import de.marinic.promptlib.common.security.SecurityConfig;
import de.marinic.promptlib.common.security.TestPrincipals;
import de.marinic.promptlib.common.security.UserDetailsServiceImpl;
import de.marinic.promptlib.execution.dto.ExecutionResponse;
import de.marinic.promptlib.user.UserRepository;
import java.time.Instant;
import java.util.List;
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
@WebMvcTest(ExecutionController.class)
@Import(SecurityConfig.class)
class ExecutionControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ExecutionService executionService;

    // See PromptControllerTest for why these unused mocks are needed here.
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private UserDetailsServiceImpl userDetailsService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void createReturns202WithLocationHeader() throws Exception {
        UUID id = UUID.randomUUID();
        UUID promptId = UUID.randomUUID();
        Instant now = Instant.now();
        ExecutionResponse response =
                new ExecutionResponse(id, promptId, 1, "PENDING", null, null, null, null, null, null, now, null);
        given(executionService.create(any(), eq(userId))).willReturn(response);

        mockMvc.perform(
                        post("/api/v1/executions")
                                .with(TestPrincipals.user(userId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"promptId":"%s","versionNo":1}
                                        """.formatted(promptId)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/executions/" + id))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createWithoutRequiredFieldsReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/executions")
                                .with(TestPrincipals.user(userId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturns200ForExistingExecution() throws Exception {
        UUID id = UUID.randomUUID();
        UUID promptId = UUID.randomUUID();
        Instant now = Instant.now();
        given(executionService.get(eq(id)))
                .willReturn(
                        new ExecutionResponse(
                                id, promptId, 1, "SUCCEEDED", "gpt-4o-mini", "output", 42, 3, 5, null, now, now));

        mockMvc.perform(get("/api/v1/executions/{id}", id).with(TestPrincipals.user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.output").value("output"));
    }

    @Test
    void getReturns404ForMissingExecution() throws Exception {
        UUID id = UUID.randomUUID();
        given(executionService.get(eq(id))).willThrow(new NotFoundException("Execution %s not found".formatted(id)));

        mockMvc.perform(get("/api/v1/executions/{id}", id).with(TestPrincipals.user(userId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsExecutionsForPrompt() throws Exception {
        UUID promptId = UUID.randomUUID();
        Instant now = Instant.now();
        given(executionService.listByPrompt(promptId))
                .willReturn(
                        List.of(
                                new ExecutionResponse(
                                        UUID.randomUUID(), promptId, 1, "SUCCEEDED", null, "out", 1, 1, 1, null, now, now)));

        mockMvc.perform(
                        get("/api/v1/executions")
                                .with(TestPrincipals.user(userId))
                                .param("promptId", promptId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"));
    }
}
