package com.example.tokenservice.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.example.tokenservice.keys.KeyConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00.789Z");

    private RSAKey rsaKey;
    private TokenService tokenService;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        rsaKey = KeyConfig.toRsaKey(generator.generateKeyPair());
        tokenService = new TokenService(
                new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey))),
                rsaKey,
                new TokenProperties("https://issuer.example", Duration.ofMinutes(15)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Authentication alice() {
        return UsernamePasswordAuthenticationToken.authenticated("alice", null,
                AuthorityUtils.createAuthorityList("ROLE_USER", "ROLE_ADMIN"));
    }

    @Test
    void issuesRs256TokenWithKidAndExpectedClaims() throws Exception {
        TokenResponse response = tokenService.issue(alice());

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);

        SignedJWT jwt = SignedJWT.parse(response.accessToken());
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.getHeader().getType()).isEqualTo(JOSEObjectType.JWT);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(rsaKey.getKeyID());
        assertThat(jwt.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()))).isTrue();

        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("https://issuer.example");
        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.getStringListClaim("roles")).containsExactly("ADMIN", "USER");
        assertThat(claims.getJWTID()).isNotBlank();
    }

    @Test
    void expiresAtIsIssuedAtPlusTtl() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(tokenService.issue(alice()).accessToken()).getJWTClaimsSet();

        assertThat(claims.getIssueTime().toInstant()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        long lifetime = (claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()) / 1000;
        assertThat(lifetime).isEqualTo(Duration.ofMinutes(15).toSeconds());
    }

    @Test
    void eachTokenHasUniqueJti() throws Exception {
        String first = SignedJWT.parse(tokenService.issue(alice()).accessToken()).getJWTClaimsSet().getJWTID();
        String second = SignedJWT.parse(tokenService.issue(alice()).accessToken()).getJWTClaimsSet().getJWTID();

        assertThat(List.of(first, second)).doesNotHaveDuplicates();
    }
}
