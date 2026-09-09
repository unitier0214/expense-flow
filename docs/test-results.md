# Test Results

## 2026-09-09（フェーズ1実装途中）

- 実行環境確認：Java 17.0.19。設計固定のJava 21ではない。
- `./mvnw -version`：成功。プロジェクト内のMaven Wrapper 3.9.11を取得して起動。
- `./mvnw --batch-mode -DskipTests compile`：失敗。`release version 21 is not supported`。Java 17ではJava 21設定を変更せず検証を続けられないため、設定は緩めていない。
- Docker：未導入。Testcontainers、`docker compose config`、Compose起動は未実行。
- ブラウザ確認：未実施。アプリを起動できるJava 21とPostgreSQLが未準備。
- GitHub：公開リポジトリを作成し、`main`へ初回コミットをpush済み。URLは `https://github.com/unitier0214/expense-flow`。
- 未実行理由：Java 21とDockerが実行環境にないため。
- 次回実行：`./mvnw test`、`./mvnw verify`、`docker compose config`、`SPRING_PROFILES_ACTIVE=demo docker compose up --build`。
