# Test Results

## 2026-09-09（フェーズ1完了確認）

実行環境はGitHub Actions [run 34348513761](https://github.com/unitier0214/expense-flow/actions/runs/34348513761) とし、Java 21、Spring Boot 4.1.1、Testcontainers PostgreSQL 17.11、Docker Composeを使用した。

- `./mvnw --batch-mode test`：成功。12テスト、失敗0、エラー0、スキップ0。
- `./mvnw --batch-mode verify`：成功。テストを含むverifyとパッケージングが完了。
- `docker compose config --quiet`：成功。
- Flyway：PostgreSQL 17上でV1マイグレーションが適用され、`flyway_schema_history`と`expense_requests`を確認。
- Spring Security：日本語ログイン画面、未認証時の`/login`リダイレクト、認証後の`/expenses`、CSRFなしPOST拒否、CSRF付きPOSTログアウトを確認。
- デモデータ：通常プロファイルでは初期化Beanがなく、demoプロファイルでは部署2件・ユーザー5件が投入され、初期化処理を再実行しても件数が増えないことを確認。
- Compose smoke test：DB readiness、アプリhealthcheck、demoログイン、認証後画面、ログアウトを確認。
- DB永続化：Composeをdown（ボリューム削除なし）してupし直した後も、DB上の変更値が保持されることを確認。
- テストの無効化・スキップ、H2やモックDBへの置換は行っていない。

## ローカルで未実行の項目

- ローカルの`./mvnw test`／`./mvnw verify`：Java 17.0.20しかなく、さらにMavenが`repo.maven.apache.org`を名前解決できなかったため、ローカルでは完了していない。Java 21を前提とする設定は変更せず、CIで同コマンドを成功させた。
- ローカルのTestcontainers／Compose：Docker CLIが未導入のため未実行。
- 実ブラウザによる目視確認：ローカルでアプリを起動できなかったため未実行。CIではcurlによるHTTP smoke testでログイン・画面表示・ログアウトを確認した。

## GitHub

- リポジトリ：https://github.com/unitier0214/expense-flow
- CI実行：[34348513761](https://github.com/unitier0214/expense-flow/actions/runs/34348513761)
