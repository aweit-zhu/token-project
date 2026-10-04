# jwks-endpoint Specification

## Purpose

以標準 JWK Set（RFC 7517）格式透過 HTTP 公開用於驗證 JWT 簽章的公鑰，讓任何下游服務都能取得公鑰自行驗證 token。

## Requirements

### Requirement: 公開 JWK Set
系統 SHALL 在 `GET /.well-known/jwks.json` 回傳 HTTP 200 與 `application/json` 內容，主體為含 `keys` 陣列的 JWK Set，該陣列包含目前簽章公鑰。

#### Scenario: 取得 JWKS
- **WHEN** 用戶端送出 `GET /.well-known/jwks.json`
- **THEN** 回應狀態為 200，`Content-Type` 為 `application/json`，主體含 `keys` 陣列且至少一個元素

### Requirement: JWK 欄位內容
JWK Set 中每把金鑰 SHALL 包含 `kty`=`RSA`、`use`=`sig`、`alg`=`RS256`、`kid`，以及 base64url 編碼的 `n` 與 `e`。

#### Scenario: JWK 欄位正確
- **WHEN** 用戶端取得 JWKS
- **THEN** 金鑰的 `kty` 為 `RSA`、`use` 為 `sig`、`alg` 為 `RS256`，`kid` 與簽發 token header 中的 `kid` 相同

### Requirement: 絕不洩漏私鑰
JWKS 回應 MUST NOT 包含任何 RSA 私鑰參數（`d`、`p`、`q`、`dp`、`dq`、`qi`）。

#### Scenario: 回應不含私鑰欄位
- **WHEN** 用戶端取得 JWKS
- **THEN** 回應中任何金鑰都不含 `d`、`p`、`q`、`dp`、`dq`、`qi` 欄位

### Requirement: 公開且可快取
JWKS 端點 SHALL 允許未驗證存取，並回傳 `Cache-Control` 標頭（`public`，`max-age` 可設定，預設 300 秒）。

#### Scenario: 匿名存取並帶快取標頭
- **WHEN** 未攜帶任何憑證的用戶端送出 `GET /.well-known/jwks.json`
- **THEN** 回應狀態為 200，且 `Cache-Control` 標頭包含 `public` 與 `max-age`
