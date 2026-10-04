package com.example.tokenservice.jwks;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * JWKS 端點設定（{@code app.jwks.*}）。
 */
@ConfigurationProperties("app.jwks")
public record JwksProperties(@DefaultValue("300s") Duration cacheMaxAge) {
}
