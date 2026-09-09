# ExpenseFlow

Java / Spring Bootで作る、架空企業向けの経費申請・承認システムです。業務ルール、認可、DB永続化、異常系テストを説明できるポートフォリオを目的とします。実在企業の業務や法令要件を再現するものではありません。

## フェーズ1の実装範囲

- Java 21、Spring Boot 4.1.1、Spring MVC、Thymeleaf
- PostgreSQL 17、Spring Data JPA、Flyway
- Spring Securityのフォームログイン、BCrypt、CSRF保護、POSTログアウト
- 全テーブルの初回Flywayマイグレーション
- demoプロファイル限定の架空ユーザー・部署・初期データ
- Docker ComposeのDB readiness、アプリhealthcheck、DB永続化

申請・承認フローは後続フェーズで実装します。フェーズ1のログイン後画面では未実装範囲を明示します。下書き削除時に履歴も削除する設計であり、完全な監査台帳ではありません。

## 起動手順

### Docker Compose（推奨）

1. `.env.example` を `.env` にコピーし、`POSTGRES_PASSWORD` を変更します。
2. `docker compose up --build` を実行します。
3. [http://localhost:8080/login](http://localhost:8080/login) を開きます。
4. 通常起動ではユーザーを自動作成しません。デモ確認時は `SPRING_PROFILES_ACTIVE=demo docker compose up --build` を使います。

デモ用ログイン情報（demoプロファイルのみ）：

- 社員：`demo.employee` / `demo-password`
- 承認者：`demo.approver.sales.1` / `demo-password`

パスワードはログへ出力しません。実運用の認証情報として使用しないでください。

DBデータは `postgres-data` ボリュームに保持されます。DBを破棄する操作はこのREADMEの通常起動手順には含めません。

### Maven Wrapper

Java 21の環境で次を実行します。

```bash
./mvnw spring-boot:run
```

DB接続先は環境変数で変更できます。詳細は `.env.example` を確認してください。

## テスト

```bash
./mvnw test
```

認証、未認証アクセス、CSRF拒否、Flyway適用をTestcontainersのPostgreSQLで検証します。Dockerが起動していない環境ではTestcontainersテストは実行できません。

## 構成資料

- [設計書](docs/design.md)
- [判断記録](docs/decisions.md)
- [進捗](docs/progress.md)
- [テスト結果](docs/test-results.md)
