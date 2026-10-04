# Spec Delta

## Purpose

以關聯式資料庫持久化保存使用者帳號與角色，作為簽發 token 時驗證身分與決定角色的唯一來源。

## ADDED Requirements

### Requirement: 使用者帳號儲存
系統 SHALL 在資料庫中保存每個使用者的帳號名稱、密碼雜湊與啟用狀態。帳號名稱 MUST 唯一且不可為空；密碼 MUST 以帶演算法前綴的雜湊格式（例如 `{bcrypt}...`）保存，不得保存明碼。

#### Scenario: 帳號名稱重複
- **WHEN** 嘗試寫入一筆帳號名稱與既有使用者相同的資料
- **THEN** 寫入被資料庫拒絕，既有使用者資料不變

#### Scenario: 密碼以雜湊保存
- **WHEN** 讀取示範使用者 `alice` 在資料庫中的密碼欄位
- **THEN** 該值以 `{bcrypt}` 開頭，且不等於明碼 `alice123`

### Requirement: 帳號名稱不分大小寫
帳號名稱 MUST 以小寫保存；以帳號登入時系統 SHALL 不分大小寫比對，且簽發 token 的 `sub` 為資料庫中保存的小寫帳號名稱（與現行行為一致）。

#### Scenario: 以大寫帳號登入
- **WHEN** 用戶端以 `ALICE` / `alice123` 呼叫 `POST /auth/token`
- **THEN** 回應狀態為 200，且 token 的 `sub` 為 `alice`

#### Scenario: 寫入含大寫的帳號名稱
- **WHEN** 嘗試寫入帳號名稱為 `Carol` 的使用者
- **THEN** 寫入被資料庫拒絕

### Requirement: 角色儲存與指派
系統 SHALL 在資料庫中保存角色（角色名稱唯一，不含 `ROLE_` 前綴），並以多對多關聯指派給使用者；一個使用者 MAY 擁有零個或多個角色，同一角色 MUST NOT 重複指派給同一使用者。

#### Scenario: 使用者擁有多個角色
- **WHEN** 查詢示範使用者 `admin` 的角色
- **THEN** 結果恰為 `ADMIN` 與 `USER`

#### Scenario: 重複指派角色
- **WHEN** 嘗試將 `USER` 角色再次指派給已擁有 `USER` 的 `alice`
- **THEN** 寫入被資料庫拒絕

### Requirement: 啟動時植入示範資料
服務啟動時 SHALL 確保資料庫具備資料表結構，並植入示範帳號 `alice`（密碼 `alice123`，角色 `USER`）與 `admin`（密碼 `admin123`，角色 `USER`、`ADMIN`），兩者皆為啟用狀態。

#### Scenario: 全新啟動
- **WHEN** 服務以空的資料庫啟動完成
- **THEN** 資料庫中存在 `alice` 與 `admin` 兩個啟用的使用者及其角色，且可用上述密碼取得 token

### Requirement: 使用者來源僅限資料庫
系統 MUST 只以資料庫中的使用者驗證身分；設定檔中的 `app.users` 項目 MUST NOT 被當作可登入的帳號。

#### Scenario: 設定檔帳號不生效
- **WHEN** 設定檔含有 `app.users` 定義的帳號 `carol`，但資料庫中沒有 `carol`
- **THEN** 以 `carol` 的帳密呼叫 `POST /auth/token` 回應 401

### Requirement: 開發用資料庫管理介面
以 `dev` profile 啟動時，系統 SHALL 在 `/h2-console` 提供 H2 資料庫管理介面，本機連線不需 Bearer token 即可開啟。未啟用 `dev` profile 時 MUST NOT 提供此介面。

#### Scenario: dev profile 本機開啟
- **WHEN** 服務以 `dev` profile 啟動，從本機以瀏覽器（不帶 token）開啟 `/h2-console/`
- **THEN** 回應狀態為 200，顯示 H2 Console 登入頁，且回應標頭 `X-Frame-Options` 為 `SAMEORIGIN`

#### Scenario: 預設 profile 不提供
- **WHEN** 服務未啟用 `dev` profile，用戶端不帶 token 請求 `/h2-console/`
- **THEN** 回應狀態為 401，不顯示 H2 Console

### Requirement: 資料庫管理介面僅限本機
即使啟用 `dev` profile，系統 MUST 拒絕來自非本機位址的 `/h2-console` 請求。

#### Scenario: 遠端連線被拒
- **WHEN** 服務以 `dev` profile 啟動，來自非本機位址的請求開啟 `/h2-console/`
- **THEN** 不顯示 H2 Console 登入頁
