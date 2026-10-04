package com.example.tokenservice.auth;

import java.time.Clock;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
public class TokenIssuerConfig {

    @Bean
    JwtEncoder jwtEncoder(JWKSet jwkSet) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(jwkSet));
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
