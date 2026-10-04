package com.example.tokenservice.jwks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.example.tokenservice.IntegrationTestSupport;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.JSONObjectUtils;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class JwksEndpointTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    RSAKey rsaKey;

    private MvcResult fetchJwks() throws Exception {
        return mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
    }

    @Test
    void anonymousClientGetsJwkSetWithPublicKey() throws Exception {
        String body = fetchJwks().getResponse().getContentAsString();

        JWKSet jwkSet = JWKSet.parse(body);
        assertThat(jwkSet.getKeys()).hasSize(1);
        RSAKey key = jwkSet.getKeys().get(0).toRSAKey();
        assertThat(key.getKeyID()).isEqualTo(rsaKey.getKeyID());
        assertThat(key.getModulus()).isEqualTo(rsaKey.getModulus());
        assertThat(key.getPublicExponent()).isEqualTo(rsaKey.getPublicExponent());
    }

    @Test
    void jwkHasExpectedFields() throws Exception {
        Map<String, Object> jwk = firstKey(fetchJwks());

        assertThat(jwk).containsEntry("kty", "RSA")
                .containsEntry("use", "sig")
                .containsEntry("alg", "RS256")
                .containsEntry("kid", rsaKey.getKeyID())
                .containsKeys("n", "e");
    }

    @Test
    void responseNeverContainsPrivateKeyParameters() throws Exception {
        Map<String, Object> jwk = firstKey(fetchJwks());

        assertThat(jwk).doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
    }

    @Test
    void responseIsPubliclyCacheable() throws Exception {
        String cacheControl = fetchJwks().getResponse().getHeader(HttpHeaders.CACHE_CONTROL);

        assertThat(cacheControl).contains("public").contains("max-age=300");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstKey(MvcResult result) throws Exception {
        Map<String, Object> json = JSONObjectUtils
                .parse(result.getResponse().getContentAsString());
        List<Object> keys = (List<Object>) json.get("keys");
        return (Map<String, Object>) keys.get(0);
    }
}
