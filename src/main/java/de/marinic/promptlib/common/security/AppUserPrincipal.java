package de.marinic.promptlib.common.security;

import de.marinic.promptlib.user.User;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Wraps our own {@link User} entity so Spring Security can work with it, without forcing the
 * entity itself to implement UserDetails. Used both by the login flow (built from the DB via
 * {@link UserDetailsServiceImpl}) and by {@link JwtAuthenticationFilter} (built straight from the
 * user id encoded in the token).
 */
public class AppUserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;

    public AppUserPrincipal(UUID id, String email, String passwordHash) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public static AppUserPrincipal from(User user) {
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash());
    }

    public UUID getId() {
        return id;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
