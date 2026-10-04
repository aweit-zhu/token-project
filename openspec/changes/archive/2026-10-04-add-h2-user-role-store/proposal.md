# Proposal

## Why

目前 `UserDetailsService` 以 `InMemoryUserDetailsManager` 讀取 `application.yml` 中寫死的 `app.users[]`，新增或停用帳號、調整角色都必須改設定檔並重新部署。改為以資料庫的「用戶、角色」資料表作為使用者來源，才能讓帳號管理與程式設定脫鉤，也為之後的帳號管理功能打底。

## What Changes

- 新增 H2 資料庫（MyBatis + H2），建立三張資料表：`users`（帳號、密碼雜湊、啟用狀態）、`roles`（角色名稱）、`user_roles`（用戶與角色的多對多關聯）。
- 以 `schema.sql` 建立資料表、`data.sql` 植入現有示範帳號 `alice`（`USER`）、`admin`（`USER`、`ADMIN`），密碼雜湊沿用現有值，對外行為不變。
- 以讀取資料庫的 `UserDetailsService` 取代 `InMemoryUserDetailsManager`；`POST /auth/token` 的驗證流程、錯誤回應、token 內容均不變。
- 新增「帳號停用」語意：`users.enabled = false` 的帳號無法取得 token，回應與帳密錯誤相同（401 `invalid_credentials`）。
- **BREAKING**（設定）：移除 `app.users[]` 設定與 `DemoUserProperties`，設定檔中的帳號不再生效。
- 預設使用 H2 記憶體模式（每次啟動由 `data.sql` 重建示範資料）；可透過 `spring.datasource.url` 改為檔案模式。
- 新增開發用 H2 Console（`/h2-console`）：只在 `dev` profile 開啟，只接受本機連線，不需 Bearer token；預設 profile 不開啟。

不在範圍內：帳號 / 角色的管理 API（CRUD）、註冊、改密碼、在 `dev` 以外的 profile 開放 H2 Console、正式資料庫（PostgreSQL 等）與 migration 工具（Flyway / Liquibase）。

## Capabilities

### New Capabilities
- `user-store`: 使用者與角色的持久化儲存——資料表結構、帳號唯一性、角色關聯、啟用狀態，以及啟動時植入的示範資料。

### Modified Capabilities
- `token-issuance`: 驗證帳密的來源由「設定的示範使用者」改為「使用者資料庫」；新增停用帳號 MUST 被拒絕（401，訊息與帳密錯誤相同）；`roles` claim 來自資料庫中該使用者的角色。

## Impact

- **相依套件**：`pom.xml` 新增 `org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3`、`com.h2database:h2`（runtime），測試加 `mybatis-spring-boot-starter-test:3.0.3`。
- **程式碼**：`auth/AuthenticationConfig.java`（改用資料庫版 `UserDetailsService`）、刪除 `auth/DemoUserProperties.java`；新增 `user` 套件（model、Mapper 介面、`UserDetailsService` 實作）。
- **資源檔**：`application.yml` 移除 `app.users`、新增 datasource / MyBatis 設定；新增 `schema.sql`、`data.sql`、`mapper/UserMapper.xml`、`application-dev.yml`（開啟 H2 Console）。
- **安全設定**：新增只在 H2 Console 開啟時生效的 `SecurityFilterChain`，放行 `/h2-console/**` 並允許同源 frame。
- **測試**：`AuthenticationConfigTest` 需改寫；整合測試（`TokenIssuanceTest` 等）依賴 `data.sql` 的示範帳號，預期不需修改。
- **文件**：README 的設定表與「示範帳號」說明需更新。
- **API**：`POST /auth/token`、`/api/me`、JWKS 端點對外行為不變。
