# ExpenseFlow

Java / Spring Bootで作る、架空企業向けの経費申請・承認システムです。業務ルール、認可、DB永続化、異常系テストを説明できるポートフォリオを目的とします。実在企業の業務や法令要件を再現するものではありません。

## 実装範囲（フェーズ4まで）

- Java 21、Spring Boot 4.1.1、Spring MVC、Thymeleaf
- PostgreSQL 17、Spring Data JPA、Flyway
- Spring Securityのフォームログイン、BCrypt、CSRF保護、POSTログアウト
- 全テーブルの初回Flywayマイグレーション
- demoプロファイル限定の架空ユーザー・部署・申請45件・履歴
- Docker ComposeのDB readiness、アプリhealthcheck、DB永続化
- 本人の経費申請一覧（状態・分類・利用日期間・件名検索、20件ページング）
- 下書きの作成・編集・削除、申請・再申請、申請詳細・履歴表示
- DRAFT / RETURNEDの編集、DRAFTのみ削除、本人だけの変更操作
- 同部署APPROVERによるDRAFT以外の詳細・履歴閲覧
- 承認待ち一覧（本人以外・同部署・SUBMITTED、20件ページング）
- 同部署の別APPROVERによる承認・理由付き差戻し、RETURNEDからの本人再申請
- PostgreSQL側の絞り込み・ページング、CSRF、楽観ロック、トランザクション内の履歴保存

CSV出力はフェーズ5で追加する予定です。下書き削除時に履歴も削除する設計であり、完全な監査台帳ではありません。

## 起動手順

### Docker Compose（推奨）

1. `.env.example` を `.env` にコピーし、`POSTGRES_PASSWORD` を変更します。
2. `docker compose up --build` を実行します。
3. [http://localhost:8080/login](http://localhost:8080/login) を開きます。
4. 通常起動ではユーザーを自動作成しません。デモ確認時は `SPRING_PROFILES_ACTIVE=demo docker compose up --build` を使います。

デモ用ログイン情報（demoプロファイルのみ。架空の公開fixtureであり、本番の秘密情報ではありません）：

- 社員：`demo.employee` / `demo-password`
- 営業部承認者1：`demo.approver.sales.1` / `demo-password`
- 営業部承認者2：`demo.approver.sales.2` / `demo-password`
- 開発部社員：`demo.employee.dev` / `demo-password`
- 開発部承認者：`demo.approver.dev` / `demo-password`

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
./mvnw verify
```

認証、未認証アクセス、CSRF拒否、Flyway適用、申請の作成・編集・申請、承認・差戻し、権限・検索・競合・履歴ロールバックをTestcontainersのPostgreSQLで検証します。Dockerが起動していない環境ではTestcontainersテストは実行できません。

## 3分デモ

1. `SPRING_PROFILES_ACTIVE=demo docker compose up --build` で起動し、`demo.employee` でログインする。
2. 新規申請を作成し、編集画面で小数金額を送信して入力エラーを確認する。元の編集先を保ったまま整数へ直して保存し、申請する。
3. `demo.approver.sales.1` へ切り替え、「承認待ち」から対象を開いて理由付き差戻しを行う。
4. `demo.employee` で理由を履歴から確認し、修正・再申請する。
5. `demo.approver.sales.2` で同じ申請を承認し、詳細の状態・履歴を確認する。
6. POSTログアウト後に保護URLへアクセスしてログインが必要なことを確認する。

詳細は [デモ手順](docs/demo-script.md) を参照してください。demoのパスワードは公開用の架空fixtureであり、実環境の秘密情報ではありません。

## 構成資料

- [設計書](docs/design.md)
- [判断記録](docs/decisions.md)
- [進捗](docs/progress.md)
- [テストケース](docs/test-cases.md)
- [テスト結果](docs/test-results.md)
- [デモ手順](docs/demo-script.md)
- [学習ガイド](docs/learning-guide.md)
