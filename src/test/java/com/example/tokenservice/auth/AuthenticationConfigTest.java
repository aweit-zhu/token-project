package com.example.tokenservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthenticationConfigTest {

    private final AuthenticationConfig config = new AuthenticationConfig();

    private final PasswordEncoder passwordEncoder = config.passwordEncoder();

    private final AuthenticationManager authenticationManager = config.authenticationManager(
            config.userDetailsService(new DemoUserProperties(List.of(
                    new DemoUserProperties.DemoUser("alice", passwordEncoder.encode("alice123"), List.of("USER")),
                    new DemoUserProperties.DemoUser("bob", "{noop}bob123", List.of("USER", "ADMIN"))))),
            passwordEncoder);

    @Test
    void correctCredentialsAuthenticate() {
        Authentication result = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("alice", "alice123"));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo("alice");
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    void noopEncodedPasswordIsSupported() {
        Authentication result = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("bob", "bob123"));

        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void wrongPasswordIsRejected() {
        assertThatExceptionOfType(BadCredentialsException.class).isThrownBy(() -> authenticationManager
                .authenticate(UsernamePasswordAuthenticationToken.unauthenticated("alice", "wrong")));
    }

    @Test
    void unknownUserIsRejectedAsBadCredentials() {
        assertThatExceptionOfType(BadCredentialsException.class).isThrownBy(() -> authenticationManager
                .authenticate(UsernamePasswordAuthenticationToken.unauthenticated("nobody", "alice123")));
    }
}
