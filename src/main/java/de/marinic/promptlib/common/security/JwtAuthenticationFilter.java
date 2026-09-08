package de.marinic.promptlib.common.security;

import de.marinic.promptlib.user.User;
import de.marinic.promptlib.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the "Authorization: Bearer <token>" header on every request, and - if the token is a
 * valid, unexpired JWT we issued - loads the user it names and puts it into the
 * SecurityContext, so downstream code (controllers, @PreAuthorize, etc.) sees an authenticated
 * request. A missing or invalid token simply leaves the context empty; whether that is allowed
 * depends entirely on SecurityConfig's authorizeHttpRequests rules for the requested path.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            if (jwtService.isValid(token)) {
                UUID userId = jwtService.extractUserId(token);
                Optional<User> user = userRepository.findById(userId);
                if (user.isPresent()) {
                    AppUserPrincipal principal = AppUserPrincipal.from(user.get());
                    var authentication =
                            new UsernamePasswordAuthenticationToken(
                                    principal, null, principal.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
