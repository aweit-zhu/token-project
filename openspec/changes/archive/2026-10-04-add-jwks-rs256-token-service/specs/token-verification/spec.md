# Spec Delta

## Purpose

讓受保護的 API 僅憑 JWKS 公開的公鑰驗證 Bearer token 的簽章、有效期與簽發者，並拒絕任何無效 token。

## ADDED Requirements

### Requirement: 受保護 API 需要有效 Bearer token
除 `/.well-known/jwks.json` 與 `POST /auth/token` 外，`/api/**` 路徑 SHALL 要求 `Authorization: Bearer <token>`；缺少或無效時 MUST 回傳 HTTP 401。

#### Scenario: 沒有 token
- **WHEN** 用戶端未帶 `Authorization` 標頭呼叫 `GET /api/me`
- **THEN** 回應狀態為 401

#### Scenario: 有效 token
- **WHEN** 用戶端以 `POST /auth/token` 取得的 token 呼叫 `GET /api/me`
- **THEN** 回應狀態為 200，主體含該 token 的 `sub` 與 `roles`

### Requirement: 以 JWKS 公鑰驗證簽章
系統 SHALL 使用 JWKS 公開的公鑰驗證 token 簽章，只接受 `alg` 為 `RS256` 的 token。

#### Scenario: 簽章被竄改
- **WHEN** 用戶端送出 payload 被修改但簽章未更新的 token
- **THEN** 回應狀態為 401

#### Scenario: 由其他私鑰簽署
- **WHEN** 用戶端送出由非本服務私鑰簽署的 RS256 token
- **THEN** 回應狀態為 401

#### Scenario: 不安全的演算法
- **WHEN** 用戶端送出 `alg` 為 `none` 或 `HS256` 的 token
- **THEN** 回應狀態為 401

### Requirement: 驗證有效期與簽發者
系統 MUST 拒絕 `exp` 已過期（容許時鐘誤差不超過 60 秒）或 `iss` 不等於設定 issuer 的 token。

#### Scenario: Token 已過期
- **WHEN** 用戶端送出 `exp` 早於現在超過 60 秒的 token
- **THEN** 回應狀態為 401

#### Scenario: Issuer 不符
- **WHEN** 用戶端送出以本服務私鑰簽署但 `iss` 為其他值的 token
- **THEN** 回應狀態為 401
