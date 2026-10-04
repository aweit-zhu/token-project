# Spec Delta

## MODIFIED Requirements

### Requirement: 以帳號密碼簽發 token
系統 SHALL 在 `POST /auth/token` 接受 JSON 主體 `{"username","password"}`，帳密與使用者資料庫中某個啟用的使用者相符時回傳 HTTP 200 與 `{"access_token","token_type":"Bearer","expires_in"}`。

#### Scenario: 帳密正確
- **WHEN** 用戶端以資料庫中啟用使用者的正確帳號與密碼呼叫 `POST /auth/token`
- **THEN** 回應狀態為 200，主體含非空的 `access_token`、`token_type` 為 `Bearer`、`expires_in` 為 token 有效秒數

### Requirement: 拒絕錯誤憑證
帳號不存在、密碼錯誤或帳號已停用時，系統 MUST 回傳 HTTP 401，且回應不得透露是帳號、密碼還是帳號狀態造成的失敗。

#### Scenario: 密碼錯誤
- **WHEN** 用戶端以存在的帳號但錯誤密碼呼叫 `POST /auth/token`
- **THEN** 回應狀態為 401，且不簽發 token

#### Scenario: 帳號不存在
- **WHEN** 用戶端以不存在的帳號呼叫 `POST /auth/token`
- **THEN** 回應狀態為 401，錯誤訊息與密碼錯誤時相同

#### Scenario: 帳號已停用
- **WHEN** 用戶端以資料庫中已停用（`enabled` 為 false）使用者的正確帳密呼叫 `POST /auth/token`
- **THEN** 回應狀態為 401，不簽發 token，且錯誤訊息與密碼錯誤時相同

### Requirement: Token claims 內容
Token SHALL 包含 `iss`（設定的 issuer）、`sub`（使用者名稱）、`iat`、`exp`（`iat` 加設定的有效期，預設 15 分鐘）、`jti`（每次簽發唯一），以及 `roles`（使用者資料庫中該使用者被指派的角色清單）。

#### Scenario: Claims 正確
- **WHEN** 示範使用者 `alice` 成功取得 token
- **THEN** token 的 `sub` 為 `alice`、`iss` 為設定的 issuer、`exp` 減 `iat` 等於設定的有效秒數，且 `roles` 為 `alice` 的角色

#### Scenario: jti 唯一
- **WHEN** 同一使用者連續取得兩個 token
- **THEN** 兩個 token 的 `jti` 不同

#### Scenario: 角色變更反映在新 token
- **WHEN** 在資料庫中為 `alice` 新增 `ADMIN` 角色後，`alice` 再次取得 token
- **THEN** 新 token 的 `roles` 為 `ADMIN` 與 `USER`
