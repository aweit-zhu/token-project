package com.example.tokenservice.user;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code users} 資料表的一列，連同 {@code user_roles} 指派的角色名稱（不含 {@code ROLE_} 前綴）。
 * 使用可變 POJO：MyBatis 的 {@code <constructor>} 不支援 {@code <collection>}。
 */
public class UserAccount {

    private Long id;
    private String username;
    private String password;
    private boolean enabled;
    private List<String> roles = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
