package com.example.tokenservice.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Token 簽發設定（{@code app.jwt.*}）。
 */
@ConfigurationProperties("app.jwt")
public record TokenProperties(
        @DefaultValue("http://localhost:8080") String issuer,
        @DefaultValue("15m") Duration ttl) {
}
