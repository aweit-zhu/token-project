# token-service

以 Spring Boot 建置的 JWT 簽發服務：用 RSA 私鑰以 **RS256** 簽發 access token，並透過 **JWKS** 端點公開公鑰，讓任何服務只憑公鑰即可驗證 token。

| 端點 | 說明 | 驗證 |
| --- | --- | --- |
| `GET /.well-known/jwks.json` | 以 JWK Set（RFC 7517）公開驗證用公鑰 | 公開 |
| `POST /auth/token` | 以帳號密碼換取 RS256 access token | 公開 |
| `GET /api/me` | 受保護的示範 API，回傳 token 代表的使用者 | Bearer token |

## 需求

- JDK 17 以上
- Maven 3.8 以上

## 啟動

```bash
mvn spring-boot:run
# 或
mvn package && java -jar target/token-service-0.0.1-SNAPSHOT.jar
```

首次啟動時若 `./keys/` 下沒有金鑰檔，服務會自動產生 2048-bit RSA 金鑰對並寫入 `./keys/private.pem` 與 `./keys/public.pem`。之後重啟會沿用同一組金鑰，因此 `kid` 不變、已簽發且未過期的 token 仍然有效。

執行測試：

```bash
mvn verify
```

## 設定

`src/main/resources/application.yml`：

| 設定 | 預設值 | 說明 |
| --- | --- | --- |
| `app.keys.private-key-path` | `./keys/private.pem` | PKCS#8 PEM 私鑰（`BEGIN PRIVATE KEY`） |
| `app.keys.public-key-path` | `./keys/public.pem` | X.509 PEM 公鑰（`BEGIN PUBLIC KEY`） |
| `app.jwt.issuer` | `http://localhost:8080` | token 的 `iss`，驗證時也會比對 |
| `app.jwt.ttl` | `15m` | token 有效期 |
| `app.jwks.cache-max-age` | `300s` | JWKS 回應的 `Cache-Control: max-age` |
| `spring.datasource.url` | `jdbc:h2:mem:tokenservice;DB_CLOSE_DELAY=-1` | 使用者資料庫（H2，預設記憶體模式） |

### 使用者資料庫

帳號與角色存放在 H2 資料庫，以 MyBatis 存取（SQL 在 `src/main/resources/mapper/UserMapper.xml`）。每次啟動會執行 `schema.sql` 建表、`data.sql` 植入示範帳號；兩支腳本都可重複執行，只補缺少的資料、不覆蓋既有資料。

| 資料表 | 欄位 | 說明 |
| --- | --- | --- |
| `users` | `id`、`username`、`password`、`enabled` | `username` 唯一且必須小寫（登入時不分大小寫）；`password` 為帶前綴的雜湊，例如 `{bcrypt}...`；`enabled = FALSE` 的帳號無法取得 token |
| `roles` | `id`、`name` | `name` 唯一，不含 `ROLE_` 前綴，例如 `USER`、`ADMIN` |
| `user_roles` | `user_id`、`role_id` | 多對多關聯，複合主鍵；刪除使用者或角色時一併刪除 |

示範帳號：`alice` / `alice123`（角色 `USER`）、`admin` / `admin123`（角色 `USER`、`ADMIN`）。

預設為記憶體模式，重啟後資料即重建。要保留資料，改用檔案模式（`data/` 已列入 `.gitignore`）：

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/tokenservice
```

新增帳號、指派角色與停用帳號的 SQL 範例（密碼雜湊可用 `new BCryptPasswordEncoder().encode(...)` 產生；開發時也可用 `{noop}明碼`）：

```sql
INSERT INTO users (username, password) VALUES ('carol', '{bcrypt}$2a$10$...');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'carol' AND r.name = 'USER';

UPDATE users SET enabled = FALSE WHERE username = 'carol';
```

角色變更在下一次取得 token 時生效；已簽發的 token 在過期前仍帶舊角色。

### H2 Console（僅開發用）

以 `dev` profile 啟動即可在瀏覽器開啟 `http://localhost:8080/h2-console` 直接查詢或修改資料表：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# 或
java -jar target/token-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

登入畫面填入 JDBC URL `jdbc:h2:mem:tokenservice`、User Name `sa`、Password 留空（改用檔案模式時填對應的 URL）。

- 不需要 Bearer token；Console 只接受本機連線，從其他機器開啟會看到 `remote connections ('webAllowOthers') are disabled`。
- 未啟用 `dev` profile 時不提供 Console，`/h2-console` 回 `401`。

### 金鑰檔規則

- 兩個檔案都存在 → 載入。
- 兩個檔案都不存在 → 自動產生並寫入（必要時建立目錄；在 POSIX 檔案系統上私鑰權限設為 `600`）。
- 只有其中一個存在、內容無法解析、公私鑰不成對、或長度小於 2048 bits → **啟動失敗**，不會自動覆蓋。

自行產生金鑰（例如多實例部署要共用同一組金鑰時）：

```bash
mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/private.pem
openssl pkey -in keys/private.pem -pubout -out keys/public.pem
```

服務只接受 PKCS#8 私鑰。若手上是 PKCS#1（`BEGIN RSA PRIVATE KEY`），先轉換：

```bash
openssl pkcs8 -topk8 -nocrypt -in pkcs1.pem -out keys/private.pem
```

`kid` 是公鑰的 RFC 7638 thumbprint（SHA-256），只由公鑰內容決定。

## 使用範例

以下為 bash；在 PowerShell 請改用 `curl.exe`，並注意 JSON 引號跳脫。

取得公鑰：

```bash
curl -i http://localhost:8080/.well-known/jwks.json
```

```json
{"keys":[{"kty":"RSA","e":"AQAB","use":"sig","kid":"<thumbprint>","alg":"RS256","n":"<modulus>"}]}
```

取得 token：

```bash
curl -s -X POST http://localhost:8080/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"alice123"}'
```

```json
{"access_token":"eyJraWQiOi...","token_type":"Bearer","expires_in":900}
```

帳號或密碼錯誤回 `401 {"error":"invalid_credentials","message":"帳號或密碼錯誤"}`，欄位缺少或空白回 `400`。

呼叫受保護 API：

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"alice123"}' | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

curl -s http://localhost:8080/api/me -H "Authorization: Bearer $TOKEN"
```

```json
{"sub":"alice","roles":["USER"],"exp":"2026-01-01T00:15:00Z"}
```

沒有 token、簽章不符、演算法不是 RS256（包含 `none`、`HS256`）、已過期（容許 60 秒時鐘誤差）或 `iss` 不符時一律回 `401`。

### Token 內容

- Header：`alg=RS256`、`typ=JWT`、`kid=<目前金鑰的 kid>`
- Claims：`iss`、`sub`、`iat`、`exp`、`jti`（每次簽發唯一）、`roles`

## 其他服務如何驗證 token

任何 Spring Boot 資源伺服器只要指向本服務的 JWKS 端點即可：

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:8080/.well-known/jwks.json
```

不要設定 `issuer-uri`（本服務沒有 OIDC discovery 文件），改在 `JwtDecoder` 上加入 issuer 驗證（`JwtValidators.createDefaultWithIssuer("http://localhost:8080")`）。其他語言可使用任何支援 JWKS 的 JWT 函式庫。

本服務自己的 `/api/**` 則直接使用與 JWKS 端點同一把公鑰驗證，不透過 HTTP 呼叫自己。

## 正式環境注意事項

- **私鑰保管**：私鑰以明文 PEM 存放於磁碟，`keys/` 已列入 `.gitignore`，絕不可提交。正式環境應改由 KMS、Vault 或容器 secret 掛載提供，並限制檔案權限（Windows 上服務無法自動設定，請自行調整 ACL）。
- **多實例部署**：各實例若各自自動產生金鑰，簽出的 token 將無法互相驗證。請預先產生一組金鑰並讓所有實例共用。
- **換鑰**：目前只支援單一金鑰。刪除 `keys/` 後重啟即會換鑰，但所有已簽發的 token 會立即失效；下游服務最多在 `cache-max-age` 後才會取得新公鑰。
- **使用者資料庫**：`data.sql` 的示範帳號僅供展示，正式環境請移除並改用正式資料庫（H2 記憶體模式重啟即遺失資料；換資料庫時需改寫 H2 方言的 SQL 腳本）。
- **H2 Console**：正式環境不可啟用 `dev` profile，Console 可直接讀寫帳號、密碼雜湊與停用狀態。
- **HTTPS**：本服務不處理 TLS，請置於 HTTPS 反向代理之後。
