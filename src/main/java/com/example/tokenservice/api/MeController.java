package com.example.tokenservice.api;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 受保護的示範 API：回傳目前 Bearer token 代表的使用者。
 */
@RestController
public class MeController {

    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.hasClaim("roles") ? jwt.getClaimAsStringList("roles") : List.of();
        return new MeResponse(jwt.getSubject(), roles, jwt.getExpiresAt());
    }

    public record MeResponse(String sub, List<String> roles, Instant exp) {
    }
}
