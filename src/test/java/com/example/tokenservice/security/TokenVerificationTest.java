package com.example.tokenservice.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.example.tokenservice.IntegrationTestSupport;
import com.example.tokenservice.keys.KeyConfig;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class TokenVerificationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    RSAKey rsaKey;

    private ResultActions callMe(String token) throws Exception {
        return mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String issueToken() throws Exception {
        String body = mvc.perform(post("/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.access_token");
    }

    private static JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("admin")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(15))))
                .jwtID(UUID.randomUUID().toString())
                .claim("roles", List.of("ADMIN", "USER"));
    }

    private String sign(JWSSigner signer, JWSAlgorithm algorithm, JWTClaimsSet claims) throws Exception {
        JWSHeader header = new JWSHeader.Builder(algorithm)
                .keyID(rsaKey.getKeyID())
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    private String signWithServiceKey(JWTClaimsSet claims) throws Exception {
        return sign(new RSASSASigner(rsaKey), JWSAlgorithm.RS256, claims);
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        callMe(issueToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value("admin"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.roles[1]").value("USER"));
    }

    @Test
    void tokenSignedByServiceKeyOutsideIssuanceEndpointIsAccepted() throws Exception {
        // 確認下方的負面測試是因為各自的缺陷被拒絕，而不是測試組 token 的方式有問題
        callMe(signWithServiceKey(validClaims().build())).andExpect(status().isOk());
    }

    @Test
    void tamperedPayloadIsRejected() throws Exception {
        String[] parts = issueToken().split("\\.");
        String forgedPayload = new String(Base64URL.from(parts[1]).decode(), StandardCharsets.UTF_8)
                .replace("\"admin\"", "\"root\"");
        String tampered = parts[0] + "." + Base64URL.encode(forgedPayload) + "." + parts[2];

        callMe(tampered).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedByAnotherPrivateKeyIsRejected() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        RSAKey otherKey = KeyConfig.toRsaKey(generator.generateKeyPair());

        // header 冒用本服務的 kid，仍應因簽章不符被拒絕
        callMe(sign(new RSASSASigner(otherKey), JWSAlgorithm.RS256, validClaims().build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unsignedTokenIsRejected() throws Exception {
        callMe(new PlainJWT(validClaims().build()).serialize()).andExpect(status().isUnauthorized());
    }

    @Test
    void hs256TokenIsRejected() throws Exception {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);

        callMe(sign(new MACSigner(secret), JWSAlgorithm.HS256, validClaims().build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = validClaims()
                .issueTime(Date.from(now.minus(Duration.ofMinutes(20))))
                .expirationTime(Date.from(now.minus(Duration.ofSeconds(90))))
                .build();

        callMe(signWithServiceKey(claims)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        callMe(signWithServiceKey(validClaims().issuer("https://evil.example").build()))
                .andExpect(status().isUnauthorized());
    }
}
