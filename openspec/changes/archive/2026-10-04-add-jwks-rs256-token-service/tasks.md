# Tasks

## 1. 專案骨架

- [x] 1.1 建立 Maven 專案 `pom.xml`（parent `spring-boot-starter-parent` 3.3.x、Java 17、groupId `com.example`、artifactId `token-service`），加入 `web`、`security`、`oauth2-resource-server`、`validation`、`test`、`spring-security-test` 相依；驗證 `mvn -q dependency:resolve` 成功
- [x] 1.2 建立 `com.example.tokenservice.TokenServiceApplication` 與 `src/main/resources/application.yml`（`app.keys.*`、`app.jwt.issuer`、`app.jwt.ttl: 15m`、`app.jwks.cache-max-age: 300s`、`app.users[]` 含 `alice`（`{bcrypt}` 雜湊、roles `USER`）與 `admin`（roles `USER, ADMIN`））；驗證 `mvn -q compile` 成功
- [x] 1.3 新增 `.gitignore`（排除 `target/`、`keys/`、IDE 檔）；驗證檔案內含 `keys/`

## 2. 簽章金鑰（signing-keys）

- [x] 2.1 實作 `KeyProperties` 與 PEM 讀寫工具（PKCS#8 私鑰、X.509 公鑰，JDK `Base64` + `KeyFactory`，偵測 PKCS#1 時給出 `openssl pkcs8` 轉換提示）；驗證單元測試：寫出再讀回的金鑰與原金鑰相等
- [x] 2.2 實作 `RsaKeyPairLoader`：兩檔皆存在 → 載入；皆不存在 → 產生 2048-bit、建立父目錄、暫存檔 + atomic move 寫入、POSIX 下私鑰設 `600`（Windows 記 warning）；驗證單元測試覆蓋「首次產生」與「既有檔不被覆寫」
- [x] 2.3 在 loader 加入一致性檢查：只有單檔存在、解析失敗、modulus 不符、長度 < 2048 皆拋 `IllegalStateException` 並帶明確訊息；驗證對應的 4 個單元測試通過
- [x] 2.4 建立 `RSAKey` bean（`use=sig`、`alg=RS256`、`keyIDFromThumbprint()`）與 `JWKSet` bean；驗證單元測試：同一組 PEM 載入兩次得到相同 kid

## 3. JWKS 端點（jwks-endpoint）

- [x] 3.1 實作 `JwksController` `GET /.well-known/jwks.json`，回傳 `jwkSet.toPublicJWKSet().toJSONObject()` 與 `Cache-Control: public, max-age=<設定值>`；暫時以最小 `SecurityFilterChain` permitAll 該路徑
- [x] 3.2 撰寫整合測試（`@SpringBootTest` + `@TempDir` 金鑰路徑）：匿名取得 200、`application/json`、`keys` 非空、`kty/use/alg/kid/n/e` 正確、不含 `d/p/q/dp/dq/qi`、`Cache-Control` 含 `public` 與 `max-age`；驗證 `mvn test -Dtest=JwksEndpointTest` 通過

## 4. Token 簽發（token-issuance）

- [x] 4.1 實作 `UserProperties`（`app.users[]`）→ `InMemoryUserDetailsManager`、DelegatingPasswordEncoder 與 `AuthenticationManager`（`DaoAuthenticationProvider`）；驗證單元測試：正確帳密認證成功、錯誤密碼拋 `BadCredentialsException`
- [x] 4.2 實作 `TokenProperties` 與 `TokenService`（`NimbusJwtEncoder`、header `alg=RS256`/`typ=JWT`/`kid`，claims `iss/sub/iat/exp/jti/roles`，注入 `Clock`）；驗證單元測試：固定 Clock 下 `exp - iat == ttl`、兩次簽發 `jti` 不同
- [x] 4.3 實作 `AuthController` `POST /auth/token`（`@Valid` DTO、成功回 `{access_token, token_type: "Bearer", expires_in}`）與 `@RestControllerAdvice`（400 驗證錯誤、401 `invalid_credentials`，帳號不存在與密碼錯誤訊息相同）；在 security 設定 permitAll `POST /auth/token`
- [x] 4.4 撰寫整合測試：帳密正確 200、密碼錯誤 401、帳號不存在 401 且主體與密碼錯誤相同、缺少 password 400、以 `/.well-known/jwks.json` 解析的公鑰（`RSASSAVerifier`）驗證簽章成功且 header `kid` 相符、`sub/iss/roles` 正確；驗證 `mvn test -Dtest=TokenIssuanceTest` 通過

## 5. Token 驗證（token-verification）

- [x] 5.1 完成 `SecurityFilterChain`（stateless、停用 CSRF/form login、`/api/**` authenticated、`oauth2ResourceServer(jwt)`），`JwtDecoder` 使用 `NimbusJwtDecoder.withPublicKey(...).signatureAlgorithm(RS256)` + `JwtValidators.createDefaultWithIssuer(issuer)`，`JwtAuthenticationConverter` 以 `roles` claim 映射 `ROLE_*`
- [x] 5.2 實作 `MeController` `GET /api/me` 回傳 `{sub, roles, exp}`；驗證 `mvn -q compile` 成功
- [x] 5.3 撰寫整合測試：無 token 401、有效 token 200 且 `sub/roles` 正確、竄改 payload 401、他鑰簽署 401、`alg=none` 401、`HS256` 401、`exp` 早於現在 > 60 秒 401、`iss` 不符 401；驗證 `mvn test -Dtest=TokenVerificationTest` 通過

## 6. 文件與整合驗證

- [x] 6.1 撰寫 `README.md`（繁體中文）：啟動方式、設定項說明、金鑰檔位置與自行用 `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048` 產生的方法、三個端點的 curl 範例、外部服務以 `jwk-set-uri` 驗證的設定範例、正式環境注意事項（私鑰保管、多實例共用金鑰、示範帳號）；驗證文件中的 curl 指令在本機實際執行結果與描述相符
- [x] 6.2 全面驗證：`mvn verify` 全部測試通過；`mvn spring-boot:run` 首次啟動產生 `keys/private.pem` 與 `keys/public.pem`，重啟後 `/.well-known/jwks.json` 的 `kid` 不變，且重啟前簽發的 token 仍可呼叫 `/api/me` 成功
