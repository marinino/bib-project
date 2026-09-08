package de.marinic.promptlib.user;

import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * prompt.owner_id has a real FK to app_user - tests that create prompts directly through
 * PromptService (bypassing the /auth/register endpoint) need an actual persisted User row,
 * not just a random UUID.
 */
public final class TestUsers {

    private TestUsers() {}

    public static User create(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        User user = new User();
        user.setEmail("test-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("irrelevant-in-these-tests"));
        return userRepository.save(user);
    }
}
