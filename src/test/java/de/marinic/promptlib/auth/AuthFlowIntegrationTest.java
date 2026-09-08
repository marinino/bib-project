package de.marinic.promptlib.auth;

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
 * End-to-end proof of the whole Stufe 4.1 chain against the real security filter chain: an
 * unauthenticated request to a protected endpoint is rejected, register/login actually issues a
 * JWT signed with our own key, and that token is accepted by JwtAuthenticationFilter on a later
 * request. No mocking of our own security classes - the whole point is the wiring between them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired private MockMvc mockMvc;

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/prompts")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithGarbageTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/prompts").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerThenLoginThenAccessProtectedEndpoint() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@example.com";

        String registerBody =
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
        String registerToken = extractToken(registerBody);

        mockMvc.perform(get("/api/v1/prompts").header("Authorization", "Bearer " + registerToken))
                .andExpect(status().isOk());

        String loginBody =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"email":"%s","password":"correct-horse-battery"}
                                                """.formatted(email)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String loginToken = extractToken(loginBody);

        mockMvc.perform(get("/api/v1/prompts").header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isOk());
    }

    @Test
    void registeringTheSameEmailTwiceReturns409() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        String body =
                """
                {"email":"%s","password":"correct-horse-battery"}
                """.formatted(email);

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        String email = "wrongpw-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"email":"%s","password":"correct-horse-battery"}
                                        """.formatted(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"email":"%s","password":"totally-wrong"}
                                        """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    private static String extractToken(String json) {
        Matcher matcher = TOKEN_PATTERN.matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("No token field in response: " + json);
        }
        return matcher.group(1);
    }
}
