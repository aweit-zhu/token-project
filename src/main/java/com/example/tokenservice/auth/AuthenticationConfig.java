package com.example.tokenservice.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * 以設定檔中的示範帳號驗證 {@code POST /auth/token} 的帳號密碼。
 */
@Configuration(proxyBeanMethods = false)
public class AuthenticationConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(DemoUserProperties properties) {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        for (DemoUserProperties.DemoUser user : properties.users()) {
            manager.createUser(User.withUsername(user.username())
                    .password(user.password())
                    .roles(user.roles().toArray(String[]::new))
                    .build());
        }
        return manager;
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder);
        provider.setUserDetailsService(userDetailsService);
        // 預設 hideUserNotFoundExceptions=true：帳號不存在也拋 BadCredentialsException，不洩漏帳號是否存在
        return new ProviderManager(provider);
    }
}
