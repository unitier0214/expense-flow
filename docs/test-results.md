# Test Results

## 2026-09-09（フェーズ2完了確認）

### 実行環境

GitHub Actions [run 34358530765](https://github.com/unitier0214/expense-flow/actions/runs/34358530765) のUbuntu runnerで、Java 21.0.12.1、Spring Boot 4.1.1、PostgreSQL 17.11、Docker Composeを使用した。検証対象コミットは [`ebe33218eef7000932f450de8103db9e0a746ef2`](https://github.com/unitier0214/expense-flow/commit/ebe33218eef7000932f450de8103db9e0a746ef2)。

### 最終結果

| 検証 | 結果 |
|---|---|
| `./mvnw --batch-mode test` | 成功。31テスト、失敗0、エラー0、スキップ0（ExpenseIntegrationTest 18、SecurityIntegrationTest 12、DemoDataInitializerIntegrationTest 1） |
| `./mvnw --batch-mode verify` | 成功。テストを含むverifyとパッケージングが完了 |
| `docker compose config --quiet` | 成功 |
| PostgreSQL 17.11 + Flyway | 成功。新規DBにV1を適用し、JPAのスキーマ検証を通過 |
| Spring Security | 成功。日本語ログイン、正常・不正ログイン、未認証リダイレクト、POSTログアウト、CSRF拒否、ログアウト後の保護URL再認証 |
| demoプロファイル | 成功。営業部・開発部と5ユーザーを冪等投入。通常プロファイルでは初期化Beanなし |
| Compose smoke | 成功。DB readiness、アプリhealthcheck、ログイン、一覧、新規作成、編集、申請、詳細・履歴、ログアウトを確認 |
| DB永続化 | 成功。Composeをdown/up（ボリューム削除なし）してもDBの更新値を保持 |

### フェーズ2の検証内容

- 作成者・部署・状態・日時をサーバー側で決定し、余計な`applicant_id`、`department_id`、`status` POSTでは変更されないことを確認。
- DRAFTの作成・編集・削除・申請、RETURNED fixtureの編集・再申請・削除拒否、SUBMITTED/APPROVEDの編集拒否を確認。
- 本人一覧だけが検索対象となり、同部署APPROVERはDRAFT以外の他人の詳細・履歴だけ表示でき、他部署・他社員は404になることをHTTPで確認。
- 件名・用途・分類・利用日、金額1〜1,000,000、整数の境界、元の小数・指数・カンマ・非数値入力の400と元入力保持を確認。
- JSTの当日境界、利用日期間の両端、状態・分類・件名検索、`%`・`_`のリテラル扱い、条件保持、0件、21件以上の20件ページングを確認。
- `@Version`の古いversion、欠落・型不正、別トランザクションの同時更新を確認し、競合時に上書きせず履歴も増えないことを確認。
- 履歴INSERTをテスト用PostgreSQLトリガーで失敗させ、経費変更と履歴が同時にロールバックすることを確認。
- ThymeleafのHTMLエスケープと、変更POSTの303、Securityのログイン・ログアウト302を確認。

### CIの修正履歴

- [run 34357536206](https://github.com/unitier0214/expense-flow/actions/runs/34357536206)：失敗。初回実装のThymeleafタイトル式、JDBC fixtureのInstant、任意検索条件のSQL型推論を検出。
- [run 34357994227](https://github.com/unitier0214/expense-flow/actions/runs/34357994227)：失敗。任意検索条件のnullパラメータ型推論が残っていたため、検索クエリを修正。
- [run 34358530765](https://github.com/unitier0214/expense-flow/actions/runs/34358530765)：成功。上記修正後の最終検証。

## フェーズ2レビュー修正（2026-09-09）

検証対象コミットは [`ff441f7a`](https://github.com/unitier0214/expense-flow/commit/ff441f7a3fa39782ddbbc54719f72b6bf7f5b4d5)。[GitHub Actions run 34394247514](https://github.com/unitier0214/expense-flow/actions/runs/34394247514) で、`./mvnw --batch-mode test`が32テスト、失敗0・エラー0・スキップ0で成功した。編集エラー後の元ID復帰、version型不正の表示、独立トランザクションの同期・楽観ロック競合検証を含む。

`./mvnw --batch-mode verify`、`docker compose config --quiet`、Compose smokeも同じrunで成功した。前回の`4077b2bd`の結果は流用していない。

### ローカルで未実行の項目

- ローカルの`./mvnw test`／`./mvnw verify`：標準Javaは17.0.20で、Java 21を明示したcompile試行も`repo.maven.apache.org`の名前解決失敗により依存取得前に終了したため未完了。
- ローカルのTestcontainers／Compose／アプリ起動：Docker CLIがないため未実行。
- 実ブラウザでの目視・スクリーンショット：ローカル起動環境がないため未実施。CIではcurlで実際のHTML、CSRF、フォーム、リダイレクトをHTTP確認した。

上記は仕様を緩めた結果ではなく、実行環境の制約である。テストの無効化・スキップ、H2やモックDBへの置換は行っていない。

## GitHub

- リポジトリ：https://github.com/unitier0214/expense-flow
- フェーズ2検証コミット：https://github.com/unitier0214/expense-flow/commit/ebe33218eef7000932f450de8103db9e0a746ef2
- フェーズ2検証CI：https://github.com/unitier0214/expense-flow/actions/runs/34358530765

## フェーズ3：承認・差戻し（2026-09-09）

### 実行環境と対象

対象SHAは [`43dcf3791b6834f540b540a46d3349e2159af7a5`](https://github.com/unitier0214/expense-flow/commit/43dcf3791b6834f540b540a46d3349e2159af7a5)。GitHub Actions [run 34397993092](https://github.com/unitier0214/expense-flow/actions/runs/34397993092) のJava 21 runner、PostgreSQL 17.11、Docker環境で実行した。過去のCI結果は今回のコードの成功判定へ流用していない。

### 結果

| 検証 | 結果 |
|---|---|
| `./mvnw --batch-mode test` | 成功。42テスト、失敗0、エラー0、スキップ0 |
| `./mvnw --batch-mode verify` | 成功。パッケージングまで完了 |
| `docker compose config --quiet` | 成功 |
| 既存Compose smoke | 成功。DB readiness、healthcheck、demoログイン、作成・編集・申請、詳細・履歴、POSTログアウト、保護URL再認証、DB永続化を確認 |
| 承認待ち一覧 | 同部署・本人以外・SUBMITTEDのみ、`submitted_at ASC, id ASC`、20件ページングを確認 |
| 承認・差戻し | APPROVERの正常処理、任意コメント、理由必須・trim・上限、RETURNED後の再申請、承認済み・差戻し済みの不正遷移を確認 |
| 認可 | 社員403、自己処理・他部署・DRAFT対象404、同部署承認者の閲覧と操作を確認 |
| 競合 | 独立PostgreSQLトランザクションで同時承認、承認対差戻しを検証し、成功1件・楽観ロック競合1件、履歴1件を確認 |
| ロールバック | APPROVE／RETURN履歴保存をテスト用DBトリガーで失敗させ、状態・version・履歴をロールバック |

初回CI [run 34397625063](https://github.com/unitier0214/expense-flow/actions/runs/34397625063) はversion欠落エラーを画面に表示していなかったため失敗した。詳細画面にversionエラー表示を追加し、上記の新しいSHAで再実行して成功した。失敗を隠すためのテスト無効化・スキップは行っていない。

### ローカル未実行

ローカルでは引き続きJava 21依存を取得するMaven Centralの名前解決とDocker CLIが利用できないため、`test`／`verify`、Testcontainers、Compose、アプリ起動、実ブラウザ目視は未実行。上記はCIで実DB・Dockerを使って検証した結果であり、curlによるHTTP確認を実ブラウザ確認とは呼んでいない。

## フェーズ4：アプリと説明資料の仕上げ（2026-09-09）

### 実行環境と対象

対象SHAは [`f7182a10c71e23e3839b22719aac88f1c365e91a`](https://github.com/unitier0214/expense-flow/commit/f7182a10c71e23e3839b22719aac88f1c365e91a)。GitHub Actions [run 34402776016](https://github.com/unitier0214/expense-flow/actions/runs/34402776016)のUbuntu runnerで、Java 21、Spring Boot 4.1.1、PostgreSQL 17.11、Docker Composeを使用した。これはPhase4対象SHAの新規実行であり、Phase3以前のCI結果を流用していない。

### 結果

| 検証 | 結果 |
|---|---|
| `./mvnw --batch-mode test` | 成功。42テスト、失敗0、エラー0、スキップ0（ExpenseIntegrationTest 19、ApprovalIntegrationTest 10、SecurityIntegrationTest 12、DemoDataInitializerIntegrationTest 1） |
| `./mvnw --batch-mode verify` | 成功。上記42テストとパッケージングが完了 |
| `docker compose config --quiet` | 成功 |
| demo初期データ | 成功。営業部23件・開発部22件、合計45件をDRAFT／SUBMITTED／RETURNED／APPROVEDへ分散し、状態と履歴を整合させて投入。再実行時に既存データを上書きしないことをTestcontainersで確認 |
| Compose smoke | 成功。healthcheck後、社員の作成→編集→申請、同部署承認者1による理由付き差戻し、社員の修正・再申請、別承認者2による承認、対象申請の状態・理由・コメント・履歴、POSTログアウト、保護URLの302、DB永続化を確認 |
| CSRF／version | 成功。smokeがログイン・各フォーム・ログアウトのHTMLから値を抽出し、固定token／versionを使わずPOSTした |
| UI整備 | 成功。共通ヘッダー、承認待ちの状態ラベル、入力エラーの対応付け、フォーカス表示、スマートフォン幅の主要フォーム操作をコードとテンプレートで整備 |

### Phase4で発生した失敗と修正

- [run 34400373220](https://github.com/unitier0214/expense-flow/actions/runs/34400373220)：失敗。Compose smokeの承認者ログイン後で停止したため、ログインLocationと承認画面HTTP statusの診断出力を追加した。
- [run 34401685105](https://github.com/unitier0214/expense-flow/actions/runs/34401685105)：失敗。承認待ち一覧HTMLに対象状態ラベルがなく、smokeの`申請中`確認が成立していなかった。承認待ち一覧へ対象行の状態ラベルを追加した。
- [run 34402776016](https://github.com/unitier0214/expense-flow/actions/runs/34402776016)：上記修正後の成功。test／verify／Compose smokeがすべて成功した。

### ローカル未実行

ローカルの標準Javaは17.0.20で、Java 21を指定したMaven実行も`repo.maven.apache.org`の名前解決失敗により依存取得前に終了した。Docker CLIがないためローカルTestcontainers、Compose、アプリ起動、実ブラウザ目視、スクリーンショット取得も未実行である。CIのcurlによる実HTTP・HTML検証をブラウザ確認とは呼んでいない。テストの無効化・スキップやH2置換は行っていない。

### フェーズ5への境界

CSV出力はまだ実装していない。Phase4の必須機能完成を [`f7182a10`](https://github.com/unitier0214/expense-flow/commit/f7182a10c71e23e3839b22719aac88f1c365e91a) として記録し、CSVはこのコミット以後の独立変更で実装する。
