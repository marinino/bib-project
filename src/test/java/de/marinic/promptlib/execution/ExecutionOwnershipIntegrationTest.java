package de.marinic.promptlib.execution;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.marinic.promptlib.TestcontainersConfiguration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression test for a gap flagged (and left open) when ownership/visibility was first added:
 * GET /executions/{id} and GET /executions?promptId= originally never checked whether the
 * requester could even read the underlying prompt. Real HTTP, real JWTs, real Postgres - same
 * pattern as PromptOwnershipIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ExecutionOwnershipIntegrationTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired private MockMvc mockMvc;

    @Test
    void executionsOfAPrivatePromptAreOnlyVisibleToItsOwner() throws Exception {
        String ownerToken = registerAndGetToken();
        String otherToken = registerAndGetToken();

        String promptBody =
                mockMvc.perform(
                                post("/api/v1/prompts")
                                        .header("Authorization", "Bearer " + ownerToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"title":"Execution Ownership Test","content":"hallo {{name}}"}
                                                """))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID promptId = UUID.fromString(extract(ID_PATTERN, promptBody));

        String executionBody =
                mockMvc.perform(
                                post("/api/v1/executions")
                                        .header("Authorization", "Bearer " + ownerToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"promptId":"%s","versionNo":1}
                                                """.formatted(promptId)))
                        .andExpect(status().isAccepted())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID executionId = UUID.fromString(extract(ID_PATTERN, executionBody));

        // Owner can read the single execution and the history.
        mockMvc.perform(
                        get("/api/v1/executions/{id}", executionId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/v1/executions")
                                .header("Authorization", "Bearer " + ownerToken)
                                .param("promptId", promptId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(executionId.toString()));

        // Someone else gets a 404 for both - same as a nonexistent id/promptId, not a 403,
        // for the same "don't leak that a private prompt exists" reasoning as PromptAccess.
        mockMvc.perform(
                        get("/api/v1/executions/{id}", executionId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        get("/api/v1/executions")
                                .header("Authorization", "Bearer " + otherToken)
                                .param("promptId", promptId.toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/prompts/{id}", promptId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    private String registerAndGetToken() throws Exception {
        String email = "exec-owner-" + UUID.randomUUID() + "@example.com";
        String body =
                mockMvc.perform(
                                post("/api/v1/auth/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"email":"%s","password":"correct-horse-battery"}
                                                """.formatted(email)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return extract(TOKEN_PATTERN, body);
    }

    private static String extract(Pattern pattern, String json) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Pattern %s not found in: %s".formatted(pattern, json));
        }
        return matcher.group(1);
    }
}
