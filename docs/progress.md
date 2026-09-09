# Progress

## フェーズ2：社員の申請機能

### フェーズ2レビュー修正（2026-09-09）

- 編集入力エラー時のフォームactionとキャンセル先をURLの@PathVariable idから固定し、送信されたidを信用しないよう修正。version型不正の日本語エラー表示も追加した。
- 編集エラー→元申請への再送信、id欠落・別ID改変、version型不正、申請件数とUPDATE履歴の維持を回帰テストへ追加した。
- 同時更新テストを独立したPostgreSQLトランザクション＋version読み取り同期＋タイムアウトへ変更し、成功1件と楽観ロック競合1件を例外型・原因まで確認した。
- 修正対象コミットは [`ff441f7a`](https://github.com/unitier0214/expense-flow/commit/ff441f7a3fa39782ddbbc54719f72b6bf7f5b4d5)、専用CIは [run 34394247514](https://github.com/unitier0214/expense-flow/actions/runs/34394247514)。32テスト、失敗0・エラー0・スキップ0で成功した。

### 実装内容

- `/expenses` を本人の申請一覧へ置き換え、状態・分類・利用日範囲・件名検索、20件ページングを実装。
- 新規作成、詳細・履歴、編集、下書き削除、申請・再申請の画面とPOSTルートを実装。経費変更の成功レスポンスは303とした。
- Controller / Form DTO / Service / Repository / Entityを分離し、申請者・部署・状態・日時は認証ユーザーとサーバーのClockから決定。
- DRAFT / RETURNEDだけ編集、DRAFTだけ削除、DRAFT / RETURNEDからSUBMITTEDへ遷移する申請操作を実装した。
- 本人は全状態を閲覧でき、同部署APPROVERはDRAFT以外の他人の詳細・履歴だけ閲覧できる。変更操作は本人だけに限定。
- CREATE / UPDATE / SUBMITの履歴を変更と同一トランザクションで保存し、再申請もSUBMITとして記録。
- `@Version` と必須nullable `Long version` による古い画面・並行更新の409、入力400、存在しない／権限外IDの404を実装。
- 金額はtrim後の半角数字列だけをBigDecimalへ変換し、範囲と整数値をサービス・ドメインの両方で検証。元入力を入力エラー画面に保持。
- Clockを注入し、利用日判定と日時表示をAsia/Tokyoに統一。`spring.jpa.open-in-view=false`は維持。
- Flyway V1は変更せず、現行スキーマで申請機能を実装。追加マイグレーションは不要と判断。

### 実行結果（2026-09-09）

検証対象コミットは [`ebe33218`](https://github.com/unitier0214/expense-flow/commit/ebe33218eef7000932f450de8103db9e0a746ef2)。[GitHub Actions run 34358530765](https://github.com/unitier0214/expense-flow/actions/runs/34358530765) がJava 21環境で成功した。

- `./mvnw --batch-mode test`：成功。31テスト、失敗0、エラー0、スキップ0。
- `./mvnw --batch-mode verify`：成功。テストを含むverifyとパッケージングが完了。
- `docker compose config --quiet`：成功。
- demoプロファイルのCompose smoke test：DB readiness、アプリhealthcheck、ログイン、一覧、新規作成、編集、申請、詳細・履歴、POSTログアウト、ログアウト後の保護URL再認証を確認。
- Composeをdown/upしてもDB上の変更値が残ることを確認し、永続ボリュームを検証。
- PostgreSQL 17.11上でFlyway V1、認証、検索、権限、CSRF、楽観ロック、履歴保存失敗時のロールバックを検証。

### ローカル実行環境の制約

- ローカルの標準Javaは17.0.20。作業用にJava 21を明示してMaven compileを試したが、`repo.maven.apache.org`の名前解決ができず、依存取得前に終了した。
- Docker CLIがないため、ローカルのTestcontainers、Compose、アプリ起動は実行していない。
- 実ブラウザによる目視確認は未実施。GitHub ActionsのCompose smokeではcurlでHTML・フォーム・CSRF・リダイレクトを確認した。
- テストを無効化・スキップしていない。H2やモックDBへの置換も行っていない。

## フェーズ1：基盤とログイン（完了）

- Java 21固定のSpring Boot 4.1.1、PostgreSQL 17、Flyway V1、Spring Securityフォームログイン、BCrypt、CSRF、POSTログアウトを実装。
- 通常プロファイルではデモユーザーを作成せず、demoプロファイルでは営業部・開発部と架空ユーザー5名を冪等投入。
- Docker ComposeのDB readiness、アプリhealthcheck、DB永続ボリューム、CIを整備。
- フェーズ1の基準CIは [run 34348513761](https://github.com/unitier0214/expense-flow/actions/runs/34348513761)。

## フェーズ3：承認・差戻し（2026-09-09）

- `/approvals` を同部署・本人以外・SUBMITTEDに限定し、`submitted_at ASC, id ASC`、20件ページングで実装した。
- 同部署の別APPROVERによる承認、理由付き差戻し、自己処理・他部署・DRAFTの拒否をServiceと検索条件で強制した。
- 承認コメントは任意500文字、差戻し理由はtrim後1〜500文字。version欠落・型不正は400、古いversion・不正遷移・DB楽観ロックは409とした。
- 状態変更とAPPROVE／RETURN履歴を同一トランザクションで保存し、差戻し後の本人編集・再申請まで接続した。
- 同時承認、承認対差戻しを独立トランザクションで検証し、成功1件・楽観ロック競合1件、履歴1件を確認した。
- 対象コミットは [`43dcf379`](https://github.com/unitier0214/expense-flow/commit/43dcf3791b6834f540b540a46d3349e2159af7a5)、CIは [run 34397993092](https://github.com/unitier0214/expense-flow/actions/runs/34397993092)。42テスト、失敗0・エラー0・スキップ0、verify・Compose smokeも成功した。

## フェーズ4への申し送り

- demoプロファイルの申請45件を状態分散・履歴整合・冪等投入へ拡張する。
- 社員の作成→編集→申請→差戻し→修正・再申請→別承認者の承認というCompose smokeと、画面・説明資料を仕上げる。
- T01〜T12の回帰を維持し、実ブラウザ確認ができない場合はHTTP検証と区別して記録する。
- CSVはフェーズ5として、必須機能完成コミットの後に独立した追加機能として実装する。
