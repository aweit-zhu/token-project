package com.example.tokenservice.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * 以 schema.sql / data.sql 初始化的 H2 驗證資料表約束與 {@link UserMapper} 的映射。每個測試結束後回滾。
 */
@MybatisTest
class UserMapperTest {

    @Autowired
    UserMapper userMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    private void insertUser(String username) {
        jdbc.update("INSERT INTO users (username, password) VALUES (?, '{noop}secret')", username);
    }

    private void assignRole(String username, String role) {
        jdbc.update("INSERT INTO user_roles (user_id, role_id) "
                + "SELECT u.id, r.id FROM users u, roles r WHERE u.username = ? AND r.name = ?", username, role);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @Test
    void demoUsersAreSeededWithRoles() {
        UserAccount alice = userMapper.findByUsername("alice");
        UserAccount admin = userMapper.findByUsername("admin");

        assertThat(alice.getRoles()).containsExactly("USER");
        assertThat(alice.isEnabled()).isTrue();
        assertThat(admin.getRoles()).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(admin.isEnabled()).isTrue();
    }

    @Test
    void passwordIsStoredAsBcryptHash() {
        String password = userMapper.findByUsername("alice").getPassword();

        assertThat(password).startsWith("{bcrypt}").isNotEqualTo("alice123");
    }

    @Test
    void userWithoutRolesHasEmptyRoleList() {
        insertUser("norole");

        UserAccount user = userMapper.findByUsername("norole");

        assertThat(user).isNotNull();
        assertThat(user.getId()).isNotNull();
        assertThat(user.getPassword()).isEqualTo("{noop}secret");
        assertThat(user.getRoles()).isEmpty();
    }

    @Test
    void unknownUserReturnsNull() {
        assertThat(userMapper.findByUsername("nobody")).isNull();
    }

    @Test
    void duplicateUsernameIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> insertUser("alice"));
    }

    @Test
    void uppercaseUsernameIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> insertUser("Carol"));
    }

    @Test
    void duplicateRoleAssignmentIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> assignRole("alice", "USER"));
    }

    @Test
    void seedScriptIsIdempotent() {
        int users = count("users");
        int roles = count("roles");
        int userRoles = count("user_roles");

        new ResourceDatabasePopulator(new ClassPathResource("schema.sql"), new ClassPathResource("data.sql"))
                .execute(dataSource);

        assertThat(count("users")).isEqualTo(users);
        assertThat(count("roles")).isEqualTo(roles);
        assertThat(count("user_roles")).isEqualTo(userRoles);
    }
}
