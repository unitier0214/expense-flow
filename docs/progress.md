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

### 実行結果

- `./mvnw -version`：成功。Maven Wrapperが3.9.11を取得して起動。
- `./mvnw --batch-mode -DskipTests compile`：Java 17環境のため、release 21非対応で失敗。依存解決とリソース処理までは実行。
- Java 21導入を試みたが、Homebrew取得を中断したため未導入。
- Docker未導入のため、TestcontainersとComposeは未実行。

### 残る制約

- Java 21を導入してコンパイル・テストを再実行する必要がある。
- Dockerを導入・起動してTestcontainers、Flyway、Composeの実DB検証を行う必要がある。
- Gitリモート未設定のため、まだpushしていない。

### 次のフェーズへの申し送り

- `docs/handoff-2026-09-09.md` を最初に読み、フェーズ1の未実行検証を完了してからフェーズ2へ進む。
