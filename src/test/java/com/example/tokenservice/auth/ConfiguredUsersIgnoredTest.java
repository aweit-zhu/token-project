package com.example.tokenservice.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tokenservice.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 舊版的 {@code app.users[]} 設定已不再是使用者來源：只存在於設定檔的帳號無法登入。
 */
@TestPropertySource(properties = {
        "app.users[0].username=carol",
        "app.users[0].password={noop}carol123",
        "app.users[0].roles[0]=USER"
})
class ConfiguredUsersIgnoredTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mvc;

    @Test
    void userDefinedOnlyInConfigurationCannotLogIn() throws Exception {
        mvc.perform(post("/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"carol\",\"password\":\"carol123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
