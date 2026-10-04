# Proposal

## Why

本專案目前是空白專案，需要一個以 Spring Boot 建置的 token 服務：用 RSA 私鑰以 RS256 簽發 JWT，並透過標準 JWKS 端點公開公鑰，讓任何服務（包含本服務自己的受保護 API）都能只憑公鑰驗證 token，而不必共享秘密。非對稱簽章 + JWKS 是 OAuth2 / OIDC 生態的標準做法，也為之後的金鑰輪替打好基礎。

## What Changes

- 建立 Spring Boot 3.x（Java 17、Maven）專案骨架。
- 新增 RSA 金鑰對管理：從設定的路徑載入 PEM 格式私鑰 / 公鑰；檔案不存在時自動產生 2048-bit 金鑰對並寫入檔案，使服務重啟後金鑰與 `kid` 保持不變。
- 新增公鑰端點 `GET /.well-known/jwks.json`，以 JWK Set（RFC 7517）格式公開公鑰（`kty`、`kid`、`use`、`alg`、`n`、`e`），絕不包含私鑰參數。
- 新增 token 簽發端點 `POST /auth/token`：以設定檔中的示範帳號驗證 username/password，成功後回傳以私鑰 RS256 簽署的 access token（header 帶 `kid`，claims 含 `iss`、`sub`、`iat`、`exp`、`jti` 等）。
- 新增受保護的示範 API（例如 `GET /api/me`），以 Spring Security OAuth2 Resource Server 透過 JWKS 公鑰驗證 Bearer token，驗證簽章、`exp` 與 `iss`。

## Capabilities

### New Capabilities
- `signing-keys`: RSA 簽章金鑰對的載入、自動產生、持久化與 `kid` 推導。
- `jwks-endpoint`: 以 JWK Set 格式公開驗證用公鑰的 HTTP 端點。
- `token-issuance`: 驗證示範帳號身分後簽發 RS256 JWT access token。
- `token-verification`: 受保護 API 使用 JWKS 公鑰驗證 Bearer token 並拒絕無效 token。

### Modified Capabilities
（無）

## Impact

- **程式碼**：新建整個 Spring Boot 專案（`pom.xml`、`src/main/java/...`、`src/main/resources/application.yml`、測試）。
- **API**：新增 `GET /.well-known/jwks.json`、`POST /auth/token`、`GET /api/me`。
- **相依套件**：`spring-boot-starter-web`、`spring-boot-starter-security`、`spring-boot-starter-oauth2-resource-server`（內含 Nimbus JOSE + JWT）、`spring-boot-starter-validation`、`spring-boot-starter-test`。
- **檔案系統**：服務需要對金鑰目錄（預設 `./keys`）有讀寫權限；私鑰檔不得進入版本控制。
- **不在範圍內**：金鑰輪替、多把金鑰並存、refresh token、資料庫使用者、OIDC discovery 文件、正式環境的密鑰管理（KMS / Vault）。
