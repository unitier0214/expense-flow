> 過去時点の記録です。最新の状態は [2026-09-12 最終修正報告](final-review-2026-09-12.md) と [テスト結果](test-results.md) を参照してください。

# ExpenseFlow 現状報告（Astra引き継ぎ用）

作成日：2026-09-09  
対象リポジトリ：https://github.com/unitier0214/expense-flow

## 結論

- フェーズ1「基盤とログイン」は完了済み。
- フェーズ2「社員の申請機能」まで実装・レビュー修正・検証済み。承認・差戻しの実操作とCSVは未着手。
- フェーズ2レビュー修正の検証対象コミットは [`ff441f7a`](https://github.com/unitier0214/expense-flow/commit/ff441f7a3fa39782ddbbc54719f72b6bf7f5b4d5)、CIは [run 34394247514](https://github.com/unitier0214/expense-flow/actions/runs/34394247514)。
- Java 21、Spring Boot 4.1.1、PostgreSQL 17.11、Testcontainers、Docker Composeで`test`、`verify`、Compose smokeが成功した。

## 最初に読むファイル

1. `docs/handoff-2026-09-09.md`
2. `docs/progress.md`
3. `docs/design.md`（設計の正本）
4. `README.md`
5. `pom.xml`
6. `src/main`
7. `src/test`
8. `.github/workflows/ci.yml`

## フェーズ1の基盤

- Java 21固定、Spring Boot 4.1.1、Spring MVC、Thymeleaf、Spring Security、Spring Data JPA、Flyway、Actuator。
- PostgreSQL 17向けFlyway V1。フェーズ2ではV1を変更せず、追加SQLも作成していない。
- フォームログイン、日本語ログイン画面、CSRF保護、POSTログアウト、未認証時のログインリダイレクト。
- 通常プロファイルではデモユーザーを作成せず、demoプロファイルでは架空の部署・ユーザーを冪等投入。
- ComposeのDB readiness、アプリhealthcheck、DB永続ボリューム。

## フェーズ2の実装範囲

- 本人の申請一覧、状態・分類・利用日期間・件名検索、20件ページング。
- DRAFT作成、詳細・履歴、DRAFT／RETURNED編集、DRAFT削除、DRAFT／RETURNEDからの申請・再申請。
- Serviceが認証usernameからDBのユーザーID・部署・roleを取得し、所有者・部署・状態・versionをサーバー側で検証。
- 本人以外の変更操作は404、同部署APPROVERの閲覧はDRAFT以外に限定。承認者自身の申請は本人として操作可能。
- CREATE／UPDATE／SUBMIT履歴、単一トランザクション、`@Version`競合検知、400／404／409／500画面。
- 金額はtrim後の`[0-9]+`だけをBigDecimalへ変換し、1〜1,000,000の整数をサービス・ドメインで検証。小数・指数・カンマ・非数値を受け入れない。
- Clockで操作時刻とJST当日判定を統一し、Instantで保存してAsia/Tokyoで表示。`open-in-view=false`を維持。
- 経費変更POSTの成功は303。Spring Securityのログイン・ログアウト302は変更していない。
- 編集エラー後もURLの@PathVariableに基づく元申請のaction・キャンセル先を維持し、version型不正を400で項目表示する。idパラメータは信用しない。

## ロールとデモ認証情報の整理

- ロールはEMPLOYEEとAPPROVERの2種類だけ。「管理者」は既存の現状報告の誤記であり、管理者ロールは実装していない。
- READMEはdemoプロファイル用の`demo.employee` / `demo-password`等を公開している。これは架空の検証用fixtureであり、実運用の秘密情報ではない。
- 実パスワード、`.env`、外部サービスの秘密情報はコミットしていない。

## 検証結果

| 検証 | 結果 |
|---|---|
| `./mvnw --batch-mode test` | 成功。32テスト、失敗0、エラー0、スキップ0 |
| `./mvnw --batch-mode verify` | 成功 |
| `docker compose config --quiet` | 成功 |
| Testcontainers PostgreSQL 17.11 | Flyway、認証、申請CRUD、履歴、検索、権限、CSRF、version競合、ロールバックを確認 |
| Compose smoke | DB readiness、healthcheck、ログイン→作成→編集→申請→詳細／履歴→ログアウト→保護URL再認証、DB永続化を確認 |

詳細な対応表と未実行理由は`docs/test-cases.md`、`docs/test-results.md`を参照する。ローカルはDocker未導入とMaven依存取得先のDNS制約により、Testcontainers／Compose／実ブラウザ目視を実行していない。CIでの検証を無効化・スキップしていない。

## フェーズ3への申し送り

- 同部署・本人以外・SUBMITTEDの承認待ち一覧。
- APPROVERの承認、理由付き差戻し、自己承認防止、承認／差戻しの競合と履歴。
- RETURNED理由の業務表示を含む承認フロー。
- 本人一覧と同じ認可・検索条件を共有するCSV、1000件上限、引用符・改行・数式対策。

## 参照

- 設計正本：[`docs/design.md`](design.md)
- 進捗：[`docs/progress.md`](progress.md)
- テストケース：[`docs/test-cases.md`](test-cases.md)
- テスト結果：[`docs/test-results.md`](test-results.md)
- デモ手順：[`docs/demo-script.md`](demo-script.md)

