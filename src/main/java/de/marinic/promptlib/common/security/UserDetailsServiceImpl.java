package de.marinic.promptlib.common.security;

import de.marinic.promptlib.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Only used by Spring Security's {@code AuthenticationManager} during login, where "username" is
 * the email the user typed. The JWT filter does NOT go through this class - it already has the
 * user id from the token and loads the user directly (see {@link JwtAuthenticationFilter}).
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository
                .findByEmail(email)
                .map(AppUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
    }
}
