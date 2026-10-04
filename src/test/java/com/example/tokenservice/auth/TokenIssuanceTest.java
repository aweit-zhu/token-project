package com.example.tokenservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tokenservice.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class TokenIssuanceTest extends IntegrationTestSupport {

    /** 修改資料的測試只動這個專屬帳號，不改動 alice / admin。 */
    private static final String TEST_USER = "issuance-test-user";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void deleteTestUser() {
        jdbc.update("DELETE FROM users WHERE username = ?", TEST_USER);
    }

    private void insertTestUser(boolean enabled, String... roles) {
        jdbc.update("INSERT INTO users (username, password, enabled) VALUES (?, '{noop}test123', ?)", TEST_USER, enabled);
        for (String role : roles) {
            assignTestUserRole(role);
        }
    }

    private void assignTestUserRole(String role) {
        jdbc.update("INSERT INTO user_roles (user_id, role_id) "
                + "SELECT u.id, r.id FROM users u, roles r WHERE u.username = ? AND r.name = ?", TEST_USER, role);
    }

    private ResultActions requestToken(String json) throws Exception {
        return mvc.perform(post("/auth/token").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String issueToken(String username, String password) throws Exception {
        String body = requestToken("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.access_token");
    }

    @Test
    void correctCredentialsReturnBearerToken() throws Exception {
        requestToken("{\"username\":\"alice\",\"password\":\"alice123\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900));
    }

    @Test
    void wrongPasswordAndUnknownUserGetIdenticalUnauthorizedResponses() throws Exception {
        String wrongPassword = requestToken("{\"username\":\"alice\",\"password\":\"nope\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.access_token").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String unknownUser = requestToken("{\"username\":\"mallory\",\"password\":\"nope\"}")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownUser).isEqualTo(wrongPassword);
        assertThat(wrongPassword).contains("invalid_credentials");
    }

    @Test
    void missingOrBlankFieldsAreBadRequests() throws Exception {
        requestToken("{\"username\":\"alice\"}").andExpect(status().isBadRequest());
        requestToken("{\"username\":\"\",\"password\":\"alice123\"}").andExpect(status().isBadRequest());
        requestToken("not json").andExpect(status().isBadRequest());
    }

    @Test
    void tokenSignatureVerifiesWithPublicKeyFromJwks() throws Exception {
        SignedJWT jwt = SignedJWT.parse(issueToken("alice", "alice123"));
        String jwksJson = mvc.perform(get("/.well-known/jwks.json"))
                .andReturn().getResponse().getContentAsString();
        RSAKey publicKey = JWKSet.parse(jwksJson).getKeyByKeyId(jwt.getHeader().getKeyID()).toRSAKey();

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.getHeader().getType().getType()).isEqualTo("JWT");
        assertThat(publicKey).as("kid in token header must be published in JWKS").isNotNull();
        assertThat(jwt.verify(new RSASSAVerifier(publicKey))).isTrue();
    }

    @Test
    void tokenCarriesExpectedClaims() throws Exception {
        JWTClaimsSet alice = SignedJWT.parse(issueToken("alice", "alice123")).getJWTClaimsSet();
        JWTClaimsSet admin = SignedJWT.parse(issueToken("admin", "admin123")).getJWTClaimsSet();

        assertThat(alice.getSubject()).isEqualTo("alice");
        assertThat(alice.getIssuer()).isEqualTo(ISSUER);
        assertThat(alice.getStringListClaim("roles")).containsExactly("USER");
        assertThat(alice.getExpirationTime().getTime() - alice.getIssueTime().getTime()).isEqualTo(900_000);
        assertThat(alice.getJWTID()).isNotBlank();
        assertThat(admin.getStringListClaim("roles")).containsExactly("ADMIN", "USER");
    }

    @Test
    void consecutiveTokensHaveDifferentJti() throws Exception {
        String first = SignedJWT.parse(issueToken("alice", "alice123")).getJWTClaimsSet().getJWTID();
        String second = SignedJWT.parse(issueToken("alice", "alice123")).getJWTClaimsSet().getJWTID();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void disabledUserGetsSameUnauthorizedResponseAsWrongPassword() throws Exception {
        insertTestUser(false, "USER");

        String disabled = requestToken("{\"username\":\"" + TEST_USER + "\",\"password\":\"test123\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.access_token").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String wrongPassword = requestToken("{\"username\":\"alice\",\"password\":\"nope\"}")
                .andReturn().getResponse().getContentAsString();

        assertThat(disabled).isEqualTo(wrongPassword);
    }

    @Test
    void usernameIsCaseInsensitiveAndSubjectIsStoredName() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(issueToken("ALICE", "alice123")).getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo("alice");
    }

    @Test
    void roleChangesAreReflectedInNewTokens() throws Exception {
        insertTestUser(true, "USER");
        assertThat(SignedJWT.parse(issueToken(TEST_USER, "test123")).getJWTClaimsSet().getStringListClaim("roles"))
                .containsExactly("USER");

        assignTestUserRole("ADMIN");

        assertThat(SignedJWT.parse(issueToken(TEST_USER, "test123")).getJWTClaimsSet().getStringListClaim("roles"))
                .containsExactly("ADMIN", "USER");
    }
}
