package com.example.tokenservice.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tokenservice.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 預設 profile 不開啟 H2 Console：{@code /h2-console} 與其他未列出的路徑一樣需要 token。
 */
class H2ConsoleDisabledTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mvc;

    @Test
    void consoleIsNotExposedWithoutDevProfile() throws Exception {
        mvc.perform(get("/h2-console/")).andExpect(status().isUnauthorized());
    }
}
