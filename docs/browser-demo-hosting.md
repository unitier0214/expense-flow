# ブラウザで試せる公開デモ

2026-09-12時点で外部環境へのデプロイは未実施。以下は公開候補の設定案であり、公開成功の記録ではない。

ExpenseFlowはJava 21のSpring BootサーバーとPostgreSQLを使用する。SitesのWorkers実行環境にはこの構成をそのまま配置できないため、現行のJava実装を試せる公開先としてRenderのDocker Web Service＋Render Postgresを候補とする。Renderは[DockerによるJavaの実行](https://render.com/docs/docker)と[PostgreSQL](https://render.com/docs/postgresql-creating-connecting)に対応している。

## 公開前に選ぶもの

- Renderの利用アカウントとリポジトリ連携。
- アプリとDBのプラン、利用期間。無料Web Serviceは15分無通信で休止し、無料Postgresは作成から30日で期限切れになる。[無料プランの仕様](https://render.com/docs/free)を確認して、応募先が閲覧する期間に合わせる。
- 公開対象のCI通過済みコミット。公開操作・契約・課金は、この資料を作成しただけでは実行しない。

## 設定案

1. PostgreSQL 17の専用デモDBを作成する。既存の業務DBを流用しない。Web Serviceと同じリージョンを選び、接続には内部ホストを使用する。
2. `unitier0214/expense-flow`からWeb Serviceを作成し、RuntimeをDocker、Dockerfileをルートの`Dockerfile`にする。Compose自体は実行しない。
3. サービスの環境変数に以下を設定する。DBパスワードはRenderの環境変数管理へ登録し、リポジトリへ書かない。

| 変数 | 値 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `demo` |
| `DB_HOST` | Render Postgresの内部ホスト |
| `DB_PORT` | `5432` |
| `DB_NAME` | 作成したDB名 |
| `DB_USERNAME` | 作成したDBユーザー |
| `DB_PASSWORD` | 作成したDBパスワード |
| `SERVER_PORT` | `10000`（Renderの`PORT`を変更した場合は同じ値にする） |
| `SERVER_ADDRESS` | `0.0.0.0` |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `framework` |
| `SERVER_SERVLET_SESSION_COOKIE_SECURE` | `true`（公開HTTPS環境専用） |

`application.yml`はこの`DB_*`をJDBC接続先へ展開する。Renderの`postgresql://...`形式の接続文字列を、そのまま`SPRING_DATASOURCE_URL`へ入れない。アプリは`jdbc:postgresql://...`形式を使う。

4. Health Check Pathを`/actuator/health`にし、公開URLのHTTPSでログイン画面を開く。Renderの[ポートとHTTPS転送の仕様](https://render.com/docs/web-services#port-binding)を参照する。
5. [デモ手順](demo-script.md)の社員作成→差戻し→再申請→承認→CSV→ログアウトを公開URLで確認する。CookieとリダイレクトがHTTPSで動くことも確認する。
6. 実行コミット、公開URL、確認日時・結果を`test-results.md`へ記録し、その後READMEに操作用リンクを追加する。

公開デモのユーザー・申請は閲覧者同士で共有される。入力内容は架空のデモデータに限定する。ここでの設定案は、外部環境での実測・動作保証を意味しない。
