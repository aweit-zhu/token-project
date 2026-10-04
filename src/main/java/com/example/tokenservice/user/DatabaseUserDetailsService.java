package com.example.tokenservice.user;

import java.util.Locale;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 從 {@code users} / {@code roles} 資料表載入使用者。帳號不分大小寫（資料表一律以小寫保存）。
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    public DatabaseUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserAccount account = userMapper.findByUsername(username.toLowerCase(Locale.ROOT));
        if (account == null) {
            throw new UsernameNotFoundException("user not found");
        }
        return User.withUsername(account.getUsername())
                .password(account.getPassword())
                .disabled(!account.isEnabled())
                .roles(account.getRoles().toArray(String[]::new))
                .build();
    }
}
