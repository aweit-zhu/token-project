package com.example.tokenservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tokenservice.IntegrationTestSupport;
import jakarta.servlet.Servlet;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;

/**
 * H2 Console 是獨立的 servlet，不經過 DispatcherServlet，因此以真實 HTTP 測試。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class H2ConsoleTest extends IntegrationTestSupport {

    private static final String REMOTE_DISABLED = "remote connections ('webAllowOthers') are disabled";

    @Autowired
    TestRestTemplate rest;

    @Autowired
    @Qualifier("h2Console")
    ServletRegistrationBean<? extends Servlet> h2Console;

    @Test
    void consoleIsReachableFromLocalhostWithoutToken() {
        ResponseEntity<String> response = rest.getForEntity("/h2-console/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        assertThat(response.getBody()).contains("H2 Console").doesNotContain(REMOTE_DISABLED);
    }

    @Test
    void consoleRejectsRemoteAddresses() throws Exception {
        // Console servlet 在第一次請求時才由 Tomcat 初始化
        rest.getForEntity("/h2-console/", String.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/h2-console/");
        request.setServletPath("/h2-console");
        request.setPathInfo("/");
        request.setRemoteAddr("203.0.113.5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        h2Console.getServlet().service(request, response);

        // H2 對遠端請求回傳說明頁（仍為 200），不顯示登入頁
        assertThat(response.getContentAsString()).contains(REMOTE_DISABLED);
    }
}
