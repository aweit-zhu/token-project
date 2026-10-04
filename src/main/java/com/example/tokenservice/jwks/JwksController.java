package com.example.tokenservice.jwks;

import java.util.Map;

import com.nimbusds.jose.jwk.JWKSet;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 以 JWK Set（RFC 7517）格式公開驗證用公鑰。
 */
@RestController
public class JwksController {

    private final Map<String, Object> publicJwks;
    private final CacheControl cacheControl;

    public JwksController(JWKSet jwkSet, JwksProperties properties) {
        // toPublicJWKSet() 會移除 d、p、q、dp、dq、qi 等所有私鑰參數
        this.publicJwks = jwkSet.toPublicJWKSet().toJSONObject();
        this.cacheControl = CacheControl.maxAge(properties.cacheMaxAge()).cachePublic();
    }

    @GetMapping(path = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok().cacheControl(cacheControl).body(publicJwks);
    }
}
