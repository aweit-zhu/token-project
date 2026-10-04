package com.example.tokenservice.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 以 mock 的 {@link UserMapper} 驗證 {@link DatabaseUserDetailsService} 搭配 {@code DaoAuthenticationProvider} 的行為。
 */
class DatabaseUserDetailsServiceTest {

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private final UserMapper userMapper = mock(UserMapper.class);

    private AuthenticationManager authenticationManager;

    @BeforeEach
    void setUp() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder);
        provider.setUserDetailsService(new DatabaseUserDetailsService(userMapper));
        authenticationManager = new ProviderManager(provider);

        given(userMapper.findByUsername("alice"))
                .willReturn(account("alice", passwordEncoder.encode("alice123"), true, List.of("USER")));
        given(userMapper.findByUsername("bob"))
                .willReturn(account("bob", "{noop}bob123", true, List.of("ADMIN", "USER")));
        given(userMapper.findByUsername("dave"))
                .willReturn(account("dave", "{noop}dave123", false, List.of("USER")));
    }

    private static UserAccount account(String username, String password, boolean enabled, List<String> roles) {
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPassword(password);
        account.setEnabled(enabled);
        account.setRoles(roles);
        return account;
    }

    private Authentication authenticate(String username, String password) {
        return authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
    }

    @Test
    void correctCredentialsAuthenticate() {
        Authentication result = authenticate("alice", "alice123");

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo("alice");
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    void multipleRolesBecomePrefixedAuthorities() {
        Authentication result = authenticate("bob", "bob123");

        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void usernameIsLowercasedBeforeLookup() {
        Authentication result = authenticate("ALICE", "alice123");

        assertThat(result.getName()).isEqualTo("alice");
        verify(userMapper).findByUsername("alice");
    }

    @Test
    void wrongPasswordIsRejected() {
        assertThatExceptionOfType(BadCredentialsException.class).isThrownBy(() -> authenticate("alice", "wrong"));
    }

    @Test
    void unknownUserIsRejectedAsBadCredentials() {
        given(userMapper.findByUsername(anyString())).willReturn(null);

        assertThatExceptionOfType(BadCredentialsException.class).isThrownBy(() -> authenticate("nobody", "alice123"));
    }

    @Test
    void disabledUserIsRejected() {
        assertThatExceptionOfType(DisabledException.class).isThrownBy(() -> authenticate("dave", "dave123"));
    }
}
