# Progress

## フェーズ1：基盤とログイン

### 実装内容

- Maven Wrapper、Java 21固定のSpring Boot 4.1.1プロジェクトを作成。
- PostgreSQL 17向けFlyway V1で全テーブル、制約、索引を作成。
- Spring Securityのフォームログイン、BCrypt、CSRF保護、POSTログアウトを実装。
- 日本語ログイン画面、共通ヘッダー、フェーズ1範囲を示すログイン後案内画面を実装。
- demoプロファイル限定で営業部・開発部と架空ユーザー5名を冪等投入。
- Docker ComposeのDB healthcheck、アプリhealthcheck、DB永続ボリューム、Dockerfile、CI設定を追加。
- Testcontainers PostgreSQL + MockMvcでT01/T12の検証コードを追加。

### 実行結果（2026-09-09）

- GitHub Actions [run 34348513761](https://github.com/unitier0214/expense-flow/actions/runs/34348513761) をJava 21で実行し、成功した。
- `./mvnw --batch-mode test`：成功。Testcontainers PostgreSQL 17.11を使い、12テスト、失敗0、エラー0、スキップ0。
- `./mvnw --batch-mode verify`：成功。テストを含むverifyが完了し、ビルド成功。
- `docker compose config --quiet`：成功。
- demoプロファイルのCompose smoke test：DB readiness、アプリhealthcheck、Flyway、PostgreSQL上のログイン、認証後画面、CSRF付きPOSTログアウトを確認し成功。
- Composeをdown/upしてもdemoユーザーの更新値が残ることを確認し、DB永続化も成功。
- 通常プロファイルで`DemoDataInitializer`が存在しないこと、demoプロファイルで部署2件・ユーザー5件が冪等投入されることをテストで確認した。

### ローカル実行環境の制約

- ローカルのJavaは17.0.20で、Java 21を要求するプロジェクト設定は変更していない。
- Docker CLIが存在しないため、ローカルのTestcontainers・Compose・ブラウザ確認は実行していない。
- ローカルMavenは`repo.maven.apache.org`を名前解決できず依存取得が完了しなかった。CIのJava 21・Docker・PostgreSQL環境で同じ検証を完了した。

### 次のフェーズへの申し送り

- フェーズ1は完了。フェーズ2以降の申請・承認機能にはまだ着手していない。
- フェーズ2では、既存のFlywayスキーマを追加マイグレーションで拡張し、設計書の権限制御・状態遷移・楽観ロックをPostgreSQL/Testcontainers前提で実装する。
