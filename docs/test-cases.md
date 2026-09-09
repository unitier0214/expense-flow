# Test Cases

フェーズ1では、T01の認証・CSRFとT12のFlyway適用を自動テストの対象にします。申請・承認のT02〜T11、T13は後続フェーズで追加します。

| ID | フェーズ1の確認 | 手段 |
|---|---|---|
| T01 | ログイン画面の公開、正常・不正ログイン、未認証アクセス、ログイン・ログアウトPOSTのCSRF拒否 | Testcontainers PostgreSQL + MockMvc |
| T12 | PostgreSQLへFlyway V1を新規適用し、JPAのスキーマ検証を通過させる | Spring Boot起動テスト |

テストはRepositoryのモックだけで済ませず、Testcontainersの実PostgreSQLでFlywayと認証ユーザーを用意します。Dockerが利用できない環境では実行できないため、理由を `docs/test-results.md` に記録します。

