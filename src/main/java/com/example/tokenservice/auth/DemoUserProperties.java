package com.example.tokenservice.auth;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 設定檔中的示範帳號（{@code app.users[]}），僅供開發 / 展示使用。
 * {@code password} 需帶 DelegatingPasswordEncoder 前綴，例如 {@code {bcrypt}...} 或 {@code {noop}...}。
 */
@ConfigurationProperties("app")
public record DemoUserProperties(List<DemoUser> users) {

    public DemoUserProperties {
        users = (users != null) ? List.copyOf(users) : List.of();
    }

    public record DemoUser(String username, String password, List<String> roles) {

        public DemoUser {
            roles = (roles != null) ? List.copyOf(roles) : List.of();
        }
    }
}
