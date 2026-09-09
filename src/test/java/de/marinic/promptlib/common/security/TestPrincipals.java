package de.marinic.promptlib.common.security;

import java.util.UUID;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * For @WebMvcTest controller tests: builds a MockMvc RequestPostProcessor that puts an
 * AppUserPrincipal into the SecurityContext before the request is dispatched, so
 * @AuthenticationPrincipal resolves in the controller under test. This has to go through the
 * real security filter chain (SecurityConfig imported, no addFilters = false) - an earlier
 * version disabled the filters to sidestep JwtAuthenticationFilter's dependencies living
 * outside the slice, but that also broke this post-processor: it only reaches
 * SecurityContextHolder via SecurityContextHolderFilter, which never runs when filters are
 * off. Fixed by mocking JwtAuthenticationFilter's dependencies instead of skipping filters.
 */
public final class TestPrincipals {

    private TestPrincipals() {}

    public static RequestPostProcessor user(UUID id) {
        AppUserPrincipal principal = new AppUserPrincipal(id, "test-" + id + "@example.com", "irrelevant-hash");
        return SecurityMockMvcRequestPostProcessors.user(principal);
    }
}
