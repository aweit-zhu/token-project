package com.example.tokenservice.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 以使用者資料庫驗證 {@code POST /auth/token} 的帳號密碼。
 */
@Configuration(proxyBeanMethods = false)
public class AuthenticationConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder);
        provider.setUserDetailsService(userDetailsService);
        // 預設 hideUserNotFoundExceptions=true：帳號不存在也拋 BadCredentialsException，不洩漏帳號是否存在
        // 停用帳號拋 DisabledException，由 ApiExceptionHandler 回與帳密錯誤相同的 401
        return new ProviderManager(provider);
    }
}
