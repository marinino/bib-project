package de.marinic.promptlib.common.security;

import java.util.UUID;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * For @WebMvcTest controller tests: builds a MockMvc RequestPostProcessor that puts an
 * AppUserPrincipal into the SecurityContext before the request is dispatched, so
 * @AuthenticationPrincipal resolves in the controller under test - independent of whether the
 * real Spring Security filter chain runs (these tests use addFilters = false, since
 * JwtAuthenticationFilter's own dependencies like UserRepository live outside a @WebMvcTest
 * slice).
 */
public final class TestPrincipals {

    private TestPrincipals() {}

    public static RequestPostProcessor user(UUID id) {
        AppUserPrincipal principal = new AppUserPrincipal(id, "test-" + id + "@example.com", "irrelevant-hash");
        return SecurityMockMvcRequestPostProcessors.user(principal);
    }
}
