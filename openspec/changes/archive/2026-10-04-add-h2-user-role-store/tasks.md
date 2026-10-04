# Tasks

## 1. 相依與資料庫設定

- [x] 1.1 在 `pom.xml` 加入 `org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3`、`com.h2database:h2`（`runtime` scope）與 `org.mybatis.spring.boot:mybatis-spring-boot-starter-test:3.0.3`（`test` scope）；驗證 `mvn -q dependency:resolve` 成功
- [x] 1.2 在 `application.yml` 新增 `spring.datasource`（`jdbc:h2:mem:tokenservice;DB_CLOSE_DELAY=-1`、`sa`、空密碼）、`spring.sql.init.mode: always`、`mybatis.mapper-locations: classpath:mapper/*.xml`、`mybatis.configuration.map-underscore-to-camel-case: true`；`.gitignore` 加入 `data/`；驗證 `.gitignore` 含 `data/`

## 2. 資料表結構與示範資料（user-store）

- [x] 2.1 新增 `src/main/resources/schema.sql`，依 design D2 建立 `users`、`roles`、`user_roles`（`IF NOT EXISTS`、唯一約束、小寫 `CHECK`、複合主鍵、`ON DELETE CASCADE` 外鍵）
- [x] 2.2 新增 `src/main/resources/data.sql`，以 `INSERT ... SELECT ... WHERE NOT EXISTS` 冪等植入角色 `USER`、`ADMIN`，使用者 `alice`、`admin`（沿用 `application.yml` 現有的 `{bcrypt}` 雜湊）與其角色關聯（子查詢依名稱取 id）
- [x] 2.3 新增 `user` 套件的 `UserAccount` POJO（`id`、`username`、`password`、`enabled`、`List<String> roles`）、`@Mapper UserMapper.findByUsername`，與 `src/main/resources/mapper/UserMapper.xml`（依 design D4 的 resultMap + LEFT JOIN）；驗證 `mvn spring-boot:run` 能啟動且無 MyBatis 映射錯誤
- [x] 2.4 撰寫 `@MybatisTest` `UserMapperTest`：示範資料存在且 `admin` 角色為 `ADMIN`、`USER`；以 `JdbcTemplate` 新增一個沒有角色的使用者，`findByUsername` 回傳的 `roles` 為空 list；查無帳號回傳 `null`；`alice` 密碼以 `{bcrypt}` 開頭；重複帳號、含大寫帳號、重複指派角色皆拋 `DataIntegrityViolationException`；以 `JdbcTemplate` 重新執行 `data.sql` 不出錯且筆數不變；驗證 `mvn test -Dtest=UserMapperTest` 通過

## 3. 以資料庫驗證身分（token-issuance）

- [x] 3.1 實作 `user.DatabaseUserDetailsService`（注入 `UserMapper`）：帳號轉小寫後查詢、找不到拋 `UsernameNotFoundException`、以 `User.withUsername(...).password(...).disabled(!enabled).roles(...)` 建立 `UserDetails`
- [x] 3.2 修改 `AuthenticationConfig`：移除 `userDetailsService(DemoUserProperties)` bean，`authenticationManager` 改注入 `DatabaseUserDetailsService`；刪除 `DemoUserProperties.java` 與 `application.yml` 的 `app.users`；驗證 `mvn -q compile` 成功且全專案無 `DemoUserProperties` 參照
- [x] 3.3 改寫 `AuthenticationConfigTest` 為 `DatabaseUserDetailsServiceTest`（mock `UserMapper`）：正確帳密通過且 authorities 為 `ROLE_USER`；大寫帳號轉小寫查詢；查無帳號與錯誤密碼皆拋 `BadCredentialsException`；停用帳號拋 `DisabledException`；驗證該測試通過
- [x] 3.4 在 `TokenIssuanceTest` 新增整合測試（以 `JdbcTemplate` 建立專屬測試帳號並在 `@AfterEach` 刪除，不改動 `alice` / `admin`）：停用帳號回 401 且主體與密碼錯誤相同；`ALICE` 登入成功且 `sub` 為 `alice`；為測試帳號新增 `ADMIN` 後新 token `roles` 為 `ADMIN`、`USER`；驗證 `mvn test -Dtest=TokenIssuanceTest` 通過且原有測試不需修改
- [x] 3.5 新增整合測試：以 `@SpringBootTest(properties = "app.users[0].username=carol" ...)` 設定一個不在資料庫的帳號，呼叫 `POST /auth/token` 回 401；驗證該測試通過
- [x] 3.6 更新 README：設定表移除 `app.users[]`、新增 datasource 說明；「示範帳號」改為說明資料表結構、`data.sql` 植入、切換 H2 檔案模式（`jdbc:h2:file:./data/tokenservice`）及新增帳號 / 停用帳號的 SQL 範例；「正式環境注意事項」更新示範帳號段落；驗證 README 中不再出現 `app.users`

## 4. 開發用 H2 Console（user-store）

- [x] 4.1 新增 `src/main/resources/application-dev.yml`，設定 `spring.h2.console.enabled: true`（不設定 `web-allow-others`）；驗證以 `dev` profile 啟動時 log 出現 `H2 console available at '/h2-console'`
- [x] 4.2 新增 `security.H2ConsoleSecurityConfig`（`@ConditionalOnProperty("spring.h2.console.enabled")`、`@Order(1)`、`securityMatcher(PathRequest.toH2Console())`、`permitAll`、停用 CSRF、`frameOptions` 為 `sameOrigin`）；驗證 `mvn -q compile` 成功
- [x] 4.3 撰寫 `H2ConsoleTest`（`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@ActiveProfiles("dev")`）：不帶 token `GET /h2-console/` 回 200 且 `X-Frame-Options` 為 `SAMEORIGIN`；以 `remoteAddr` 為非本機位址的請求直接呼叫 Console servlet，不回傳登入頁；另在預設 profile 的整合測試中驗證 `GET /h2-console/` 回 401；驗證這些測試通過
- [x] 4.4 更新 README：說明以 `--spring.profiles.active=dev` 開啟 `/h2-console`、登入用的 JDBC URL / 帳密、只限本機，並在「正式環境注意事項」標明不可啟用 `dev` profile；驗證 README 含 `/h2-console`

## 5. 整體驗證

- [x] 5.1 執行 `mvn clean verify` 全部測試通過；以 `dev` profile 啟動後用 README 的 curl 範例取得 `alice` token 並呼叫 `/api/me` 回 200，且 `GET /h2-console/` 回 200
