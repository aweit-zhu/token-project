# token-issuance Specification

## Purpose

驗證設定檔中示範帳號的身分後，以 RSA 私鑰 RS256 簽發 JWT access token，供用戶端呼叫受保護的 API。

## Requirements

### Requirement: 以帳號密碼簽發 token
系統 SHALL 在 `POST /auth/token` 接受 JSON 主體 `{"username","password"}`，帳密與設定的示範使用者相符時回傳 HTTP 200 與 `{"access_token","token_type":"Bearer","expires_in"}`。

#### Scenario: 帳密正確
- **WHEN** 用戶端以正確的示範帳號與密碼呼叫 `POST /auth/token`
- **THEN** 回應狀態為 200，主體含非空的 `access_token`、`token_type` 為 `Bearer`、`expires_in` 為 token 有效秒數

### Requirement: 拒絕錯誤憑證
帳號不存在或密碼錯誤時，系統 MUST 回傳 HTTP 401，且回應不得透露是帳號還是密碼錯誤。

#### Scenario: 密碼錯誤
- **WHEN** 用戶端以存在的帳號但錯誤密碼呼叫 `POST /auth/token`
- **THEN** 回應狀態為 401，且不簽發 token

#### Scenario: 帳號不存在
- **WHEN** 用戶端以不存在的帳號呼叫 `POST /auth/token`
- **THEN** 回應狀態為 401，錯誤訊息與密碼錯誤時相同

### Requirement: 驗證請求格式
`username` 或 `password` 缺少或為空字串時，系統 MUST 回傳 HTTP 400。

#### Scenario: 缺少密碼
- **WHEN** 用戶端呼叫 `POST /auth/token` 但主體沒有 `password`
- **THEN** 回應狀態為 400

### Requirement: Token 以 RS256 簽署並帶 kid
簽發的 token SHALL 為 JWS Compact 格式，header 的 `alg` 為 `RS256`、`typ` 為 `JWT`、`kid` 等於 JWKS 中目前金鑰的 `kid`。

#### Scenario: 以 JWKS 公鑰驗證簽章
- **WHEN** 取得一個新簽發的 token，並以 `GET /.well-known/jwks.json` 中相同 `kid` 的公鑰驗證
- **THEN** 簽章驗證成功

### Requirement: Token claims 內容
Token SHALL 包含 `iss`（設定的 issuer）、`sub`（使用者名稱）、`iat`、`exp`（`iat` 加設定的有效期，預設 15 分鐘）、`jti`（每次簽發唯一），以及 `roles`（該使用者的角色清單）。

#### Scenario: Claims 正確
- **WHEN** 示範使用者 `alice` 成功取得 token
- **THEN** token 的 `sub` 為 `alice`、`iss` 為設定的 issuer、`exp` 減 `iat` 等於設定的有效秒數，且 `roles` 為 `alice` 的角色

#### Scenario: jti 唯一
- **WHEN** 同一使用者連續取得兩個 token
- **THEN** 兩個 token 的 `jti` 不同
