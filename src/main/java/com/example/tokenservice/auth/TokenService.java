package com.example.tokenservice.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.jwk.RSAKey;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * 以 RSA 私鑰 RS256 簽發 access token。
 */
@Service
public class TokenService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtEncoder jwtEncoder;
    private final String keyId;
    private final TokenProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder jwtEncoder, RSAKey rsaKey, TokenProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.keyId = rsaKey.getKeyID();
        this.properties = properties;
        this.clock = clock;
    }

    public TokenResponse issue(Authentication authentication) {
        // JWT 的時間欄位以秒為單位，先截掉毫秒，讓 exp - iat 恰好等於 ttl
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(properties.ttl());
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith(ROLE_PREFIX)
                        ? authority.substring(ROLE_PREFIX.length()) : authority)
                .sorted()
                .toList();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).type("JWT").build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(authentication.getName())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return TokenResponse.bearer(token, properties.ttl().toSeconds());
    }
}
