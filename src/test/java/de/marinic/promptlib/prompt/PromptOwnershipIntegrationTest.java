package de.marinic.promptlib.prompt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.marinic.promptlib.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end proof (real HTTP layer, real JWTs, real Postgres) that a private prompt is
 * invisible to everyone but its owner - a missing prompt and someone else's private prompt come
 * back as the same 404, see {@link PromptAccess#requireReadable} - and that only the owner may
 * modify or delete it, even once it has been made public.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PromptOwnershipIntegrationTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired private MockMvc mockMvc;
    @Autowired private PromptRepository promptRepository;

    private final List<UUID> createdPromptIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdPromptIds.forEach(promptRepository::deleteById);
        createdPromptIds.clear();
    }

    @Test
    void privatePromptIsHiddenFromOthersAndVisibleOnceMadePublic() throws Exception {
        String ownerToken = registerAndGetToken();
        String otherToken = registerAndGetToken();

        String createBody =
                mockMvc.perform(
                                post("/api/v1/prompts")
                                        .header("Authorization", "Bearer " + ownerToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"title":"Secret Prompt","content":"top secret content"}
                                                """))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID promptId = UUID.fromString(extract(ID_PATTERN, createBody));
        createdPromptIds.add(promptId);

        // Owner can read it fine.
        mockMvc.perform(get("/api/v1/prompts/{id}", promptId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // Someone else gets exactly the same 404 a nonexistent id would give - not a 403,
        // which would leak that a private prompt with this id exists at all.
        mockMvc.perform(get("/api/v1/prompts/{id}", promptId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        // Nor does it show up in the other user's search results.
        mockMvc.perform(
                        get("/api/v1/prompts")
                                .header("Authorization", "Bearer " + otherToken)
                                .param("query", "Secret Prompt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Someone else can never modify or delete it, private or not.
        mockMvc.perform(
                        patch("/api/v1/prompts/{id}", promptId)
                                .header("Authorization", "Bearer " + otherToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"title":"Hijacked"}
                                        """))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/prompts/{id}", promptId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        // Owner flips visibility to PUBLIC.
        mockMvc.perform(
                        patch("/api/v1/prompts/{id}", promptId)
                                .header("Authorization", "Bearer " + ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"visibility":"PUBLIC"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));

        // Now the other user can read it (still can't own/modify it) ...
        mockMvc.perform(get("/api/v1/prompts/{id}", promptId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/prompts/{id}", promptId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        // ... and it shows up in their search now, too.
        mockMvc.perform(
                        get("/api/v1/prompts")
                                .header("Authorization", "Bearer " + otherToken)
                                .param("query", "Secret Prompt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Only the owner can delete it.
        mockMvc.perform(delete("/api/v1/prompts/{id}", promptId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
        createdPromptIds.remove(promptId);
    }

    private String registerAndGetToken() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.com";
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
