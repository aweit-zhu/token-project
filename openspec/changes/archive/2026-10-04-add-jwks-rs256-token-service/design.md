# Design

## Context

- 專案目錄 `D:\AI\token-project` 目前除 `openspec/` 外沒有任何程式碼，為全新專案（greenfield）。
- 開發環境：JDK 17.0.8、Maven 3.8.1（另有 Gradle 5.6.4，版本過舊不採用）。
- 動機與範圍見 `proposal.md`；行為契約見 `specs/signing-keys`、`specs/jwks-endpoint`、`specs/token-issuance`、`specs/token-verification`。
- 已與使用者確認：範圍包含簽發 + JWKS + 驗證；金鑰從 PEM 檔載入、缺檔時自動產生；登入以設定檔中的示範帳號驗證。

## Goals / Non-Goals

**Goals:**
- 單一 Spring Boot 應用同時扮演「授權伺服器（簽發 token + 公開 JWKS）」與「資源伺服器（驗證 token）」的角色。
- 簽章、JWKS、驗證三者共用同一份金鑰來源，確保 `kid` 一致。
- 只依賴 Spring Boot 官方 starter，不引入 BouncyCastle 等額外加密函式庫。
- 每個 spec scenario 都有對應的自動化測試。

**Non-Goals:**
- 不採用 Spring Authorization Server（完整 OAuth2 / OIDC 流程對本需求過重）。
- 不支援多把金鑰或金鑰輪替，但資料結構保留以 `JWKSet` 表示，日後可擴充。
- 不處理 HTTPS / 反向代理設定。

## Decisions

### D1. 技術棧：Spring Boot 3.3.x + Maven + Java 17
- 使用 `spring-boot-starter-parent` 3.3.x（支援 Java 17）。groupId `com.example`、artifactId `token-service`、根套件 `com.example.tokenservice`（假設，可於實作前更改）。
- `spring-boot-starter-oauth2-resource-server` 已帶入 Nimbus JOSE + JWT 與 `spring-security-oauth2-jose`，簽發與驗證都用它，不需額外相依。
- 替代方案：Gradle（本機版本 5.6.4 不支援 Spring Boot 3）、jjwt（需另外處理 JWK 序列化，與資源伺服器整合度較差）。

### D2. 金鑰載入與產生（`signing-keys`）
- `KeyProperties`（`@ConfigurationProperties("app.keys")`）：`private-key-path`（預設 `./keys/private.pem`）、`public-key-path`（預設 `./keys/public.pem`）。
- `RsaKeyPairLoader` 在啟動時決定行為：
  - 兩檔皆存在 → 讀取並解析。
  - 兩檔皆不存在 → `KeyPairGenerator.getInstance("RSA")` 產生 2048-bit，寫成 PEM（私鑰 `-----BEGIN PRIVATE KEY-----` PKCS#8、公鑰 `-----BEGIN PUBLIC KEY-----` X.509），先寫暫存檔再 `ATOMIC_MOVE` 以避免半寫入；在支援 POSIX 的檔案系統將私鑰權限設為 `600`（Windows 略過並記錄 log）。
  - 只有一檔存在、解析失敗、modulus 不符、長度 < 2048 → 拋出 `IllegalStateException`，讓 Spring 啟動失敗。
- PEM 解析以 JDK 內建 `Base64.getMimeDecoder()` + `KeyFactory`（`PKCS8EncodedKeySpec` / `X509EncodedKeySpec`）實作，避免引入 BouncyCastle。不支援 PKCS#1（`BEGIN RSA PRIVATE KEY`），錯誤訊息會提示用 `openssl pkcs8 -topk8 -nocrypt` 轉換。
- 產出一個 `RSAKey`（Nimbus）bean：`keyUse(SIGNATURE)`、`algorithm(RS256)`、`keyIDFromThumbprint()`（RFC 7638 SHA-256），使 kid 只由公鑰決定、重啟後不變。
- 替代方案：kid 用 UUID（重啟即改變，違反 spec）、Java KeyStore（使用者選擇 PEM）。

### D3. JWKS 端點（`jwks-endpoint`）
- 以 `RSAKey` 建立 `JWKSet` bean；`JwksController` 的 `GET /.well-known/jwks.json` 回傳 `jwkSet.toPublicJWKSet().toJSONObject()`。`toPublicJWKSet()` 由 Nimbus 移除所有私鑰參數，測試再額外斷言不含 `d/p/q/dp/dq/qi`。
- 回應加上 `Cache-Control: public, max-age=<app.jwks.cache-max-age>`（預設 `300s`）。
- 替代方案：直接用 `RSAPublicKey` 自行組 JSON（容易漏欄位或編碼錯誤）。

### D4. Token 簽發（`token-issuance`）
- `TokenProperties`（`app.jwt`）：`issuer`（預設 `http://localhost:8080`）、`ttl`（`Duration`，預設 `15m`）。
- 簽章器：`NimbusJwtEncoder(new ImmutableJWKSet<>(jwkSet))`；`JwsHeader.with(SignatureAlgorithm.RS256).keyId(kid).type("JWT")`。
- `TokenService.issue(Authentication)` 組 claims：`iss`、`sub`、`iat`、`exp = iat + ttl`、`jti = UUID`、`roles`（去掉 `ROLE_` 前綴的角色清單）。時間來源注入 `java.time.Clock`，方便測試。
- `AuthController`：`POST /auth/token`，請求 DTO 以 `@NotBlank` 驗證（失敗 → 400）；透過 `AuthenticationManager`（`DaoAuthenticationProvider` + `InMemoryUserDetailsManager`）驗證帳密。`BadCredentialsException` 與 `UsernameNotFoundException` 一律回 401，訊息固定為 `invalid_credentials`（`DaoAuthenticationProvider` 預設 `hideUserNotFoundExceptions=true`）。
- 示範使用者來自 `app.users[]`（`username`、`password`、`roles`）；密碼經 `PasswordEncoderFactories.createDelegatingPasswordEncoder()` 比對，設定檔範例使用 `{bcrypt}` 雜湊，也允許開發時用 `{noop}`。
- 錯誤回應格式統一為 `{"error": "<code>", "message": "<說明>"}`，由 `@RestControllerAdvice` 處理。
- 替代方案：HTTP Basic 換 token（使用者選擇 JSON 帳密）。

### D5. Token 驗證（`token-verification`）
- `SecurityFilterChain`：stateless、停用 CSRF 與 form login；`permitAll` 給 `GET /.well-known/jwks.json` 與 `POST /auth/token`、`/api/**` 需 `authenticated`；`oauth2ResourceServer(jwt)`。
- `JwtDecoder`：`NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).signatureAlgorithm(RS256).build()`，再設定 `JwtValidators.createDefaultWithIssuer(issuer)`（含 `JwtTimestampValidator` 預設 60 秒時鐘誤差）。只允許 RS256，因此 `alg: none` / `HS256` 會被拒絕。
- 公鑰與 JWKS 端點出自同一個 `RSAKey` bean，等同「以 JWKS 公鑰驗證」。外部服務則設定 `spring.security.oauth2.resourceserver.jwt.jwk-set-uri=<本服務>/.well-known/jwks.json`。
- 替代方案：本服務的 decoder 也用 `jwk-set-uri` 指向自己——需要自我 HTTP 呼叫，啟動時序與連接埠綁定較脆弱，且測試需真實伺服器，故不採用。
- `JwtAuthenticationConverter`：`authoritiesClaimName = "roles"`、`authorityPrefix = "ROLE_"`。
- `MeController`：`GET /api/me` 回傳 `{"sub", "roles", "exp"}`。

### D6. 測試策略
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`（或 `MockMvc`），金鑰路徑以 `@DynamicPropertySource` 指向 JUnit `@TempDir`，避免汙染 `./keys`。
- 金鑰載入器以單元測試覆蓋：產生、重載後 kid 相同、單檔存在、不成對、1024-bit。
- 簽章驗證測試：從 `/.well-known/jwks.json` 取 JSON → `JWKSet.parse` → `RSASSAVerifier` 驗證 `/auth/token` 回傳的 token。
- 負面 token（竄改、他鑰簽署、`alg=none`、`HS256`、過期、iss 不符）由測試以 Nimbus 直接組出。

## Risks / Trade-offs

- [私鑰以明文 PEM 存在磁碟] → 預設目錄 `./keys` 加入 `.gitignore`；POSIX 上設 `600`；文件說明正式環境應改用 KMS / Vault 或掛載 secret。
- [示範帳號寫在設定檔] → 使用 bcrypt 雜湊；README 標明僅供示範。
- [無金鑰輪替：私鑰外洩只能整體換鑰，所有已簽 token 失效] → 刪除 `keys/` 後重啟即換鑰；以 `JWKSet` 為資料結構，日後可擴充多 key。
- [多實例部署時各自產生不同金鑰] → 文件說明多實例須共用同一組 PEM 檔（預先產生並掛載），自動產生僅適用單機 / 開發。
- [JWKS 快取 300 秒] → 換鑰後下游最多 5 分鐘才更新；可透過 `app.jwks.cache-max-age` 調整。
- [Windows 無法設定 POSIX 權限] → 記錄 warning，不阻擋啟動。
