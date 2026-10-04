# Spec Delta

## Purpose

管理用於簽署 JWT 的 RSA 金鑰對：從設定的位置載入、在缺少時自動產生並持久化，並為公鑰推導穩定的金鑰識別碼（kid）。

## ADDED Requirements

### Requirement: 從設定路徑載入 PEM 金鑰對
系統 SHALL 在啟動時從設定的私鑰與公鑰檔案路徑載入 PEM 格式的 RSA 金鑰對（私鑰為 PKCS#8，公鑰為 X.509 SubjectPublicKeyInfo）。

#### Scenario: 金鑰檔已存在
- **WHEN** 服務啟動且設定路徑下已存在有效的私鑰與公鑰 PEM 檔
- **THEN** 系統使用這組金鑰對進行簽章與公開，且不覆寫既有檔案

### Requirement: 缺少金鑰時自動產生並持久化
當私鑰與公鑰檔案皆不存在時，系統 SHALL 產生長度至少 2048 bits 的新 RSA 金鑰對，並以 PEM 格式寫入設定路徑，必要時建立父目錄。

#### Scenario: 首次啟動沒有金鑰
- **WHEN** 服務啟動且設定路徑下沒有任何金鑰檔
- **THEN** 系統產生新的 RSA 金鑰對、寫入私鑰與公鑰 PEM 檔，並以此金鑰對提供服務

#### Scenario: 重啟後沿用同一組金鑰
- **WHEN** 服務在首次啟動產生金鑰後重新啟動
- **THEN** 系統載入先前寫入的金鑰，重啟前簽發的未過期 token 仍可通過驗證

### Requirement: 金鑰檔不一致時拒絕啟動
系統 MUST 在金鑰檔狀態不一致或內容無效時啟動失敗並提供明確錯誤訊息，而不是默默產生新金鑰。

#### Scenario: 只存在其中一個金鑰檔
- **WHEN** 服務啟動且只有私鑰檔或只有公鑰檔存在
- **THEN** 系統啟動失敗，錯誤訊息指出缺少的檔案

#### Scenario: 公私鑰不成對
- **WHEN** 服務啟動且私鑰與公鑰的 modulus 不相符
- **THEN** 系統啟動失敗，錯誤訊息指出金鑰對不相符

#### Scenario: 金鑰長度不足
- **WHEN** 服務啟動且載入的 RSA 金鑰長度小於 2048 bits
- **THEN** 系統啟動失敗

### Requirement: 穩定的金鑰識別碼
系統 SHALL 以公鑰內容推導 kid（RFC 7638 JWK Thumbprint，SHA-256，base64url），使同一把公鑰永遠得到相同的 kid。

#### Scenario: 相同公鑰得到相同 kid
- **WHEN** 服務以同一組金鑰檔重啟
- **THEN** JWKS 中與簽發 token header 中的 kid 與重啟前相同
