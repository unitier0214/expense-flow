# Test Results

## 2026-09-12 最終総合レビュー修正

R1〜R6を修正。実装検証対象は`b9ca840a6d29692aa4bca281c9ed1f0d527f1ace`、[CI run 34681842105](https://github.com/unitier0214/expense-flow/actions/runs/34681842105)はsuccess。test／verify各57件、失敗・エラー・スキップ0。Compose、Chromium、Maven起動、SBOM検査も成功し、掲載画像5枚を目視した。画像掲載・結果追記後のCIと未確認範囲は [最終修正報告](final-review-2026-09-12.md) と [PR #1](https://github.com/unitier0214/expense-flow/pull/1) を参照。

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

## フェーズ5：CSV出力（2026-09-09）

### 実行環境と対象

CSV実装の検証対象は [`41f90066a190dc8f0f78faba1849f4b044b19722`](https://github.com/unitier0214/expense-flow/commit/41f90066a190dc8f0f78faba1849f4b044b19722) である。GitHub Actions [run 34406014385](https://github.com/unitier0214/expense-flow/actions/runs/34406014385) のUbuntu runner、Java 21.0.12、Spring Boot 4.1.1、PostgreSQL 17.11、Docker Composeで実行した。Phase4以前の成功CIをCSV追加の成功結果へ流用していない。

### 結果

| 検証 | 結果 |
|---|---|
| `./mvnw --batch-mode test` | 成功。49テスト、失敗0・エラー0・スキップ0（Expense 19、Approval 10、CSV 7、Security 12、Demo initializer 1） |
| `./mvnw --batch-mode verify` | 成功。49テストとパッケージングが完了 |
| `docker compose config --quiet` | 成功 |
| CSV認可・検索 | 成功。本人だけ、状態・分類・利用日期間・件名の条件、両端含み、`updated_at DESC, id DESC`を確認 |
| CSV形式 | 成功。UTF-8 BOM、日本語ヘッダー・状態・JST日時、固定ファイル名、RFC 4180相当の引用、カンマ・改行・引用符、数式対策を確認 |
| CSV件数 | 成功。0件はヘッダーのみ、1,000件は全行、1,001件は400で絞り込みを案内し、黙って切り捨てないことを確認 |
| Compose smoke | 成功。社員の作成→編集→申請→差戻し→修正・再申請→承認→詳細・履歴→ログアウト、検索条件付きCSV、保護URL拒否、DB永続化を確認 |

### CSV追加時の失敗と修正

- [run 34405496732](https://github.com/unitier0214/expense-flow/actions/runs/34405496732)：失敗。`CsvIntegrationTest`の固定Clockが本番のClock Beanと重複し、`NoUniqueBeanDefinitionException`で7件のテストコンテキストが起動できなかった。
- 修正コミット [`41f90066`](https://github.com/unitier0214/expense-flow/commit/41f90066a190dc8f0f78faba1849f4b044b19722) でテスト専用固定Clockを`@Primary`にし、本番コードのClock構成を変更せずにテストの意図を明確化した。
- [run 34406014385](https://github.com/unitier0214/expense-flow/actions/runs/34406014385)：上記修正後の成功。test／verify／Compose設定／Compose smokeがすべて成功した。

### ローカル未実行

- ローカルの`./mvnw --batch-mode test`／`verify`：標準Javaは17.0.20で、Java 21を指定した実行も`repo.maven.apache.org`の名前解決失敗により依存取得前に終了した。
- ローカルのTestcontainers／Compose／アプリ起動：Docker CLIがないため未実行。
- 実ブラウザ操作・目視・スクリーンショット：ローカルでアプリを起動できず、ブラウザ環境もないため未実施。CIのcurlによるHTML／HTTP検証をブラウザ確認とは呼んでいない。

上記の未実行は仕様を緩めた結果ではない。テストの無効化・スキップ、H2やモックDBへの置換は行っていない。

## 最終レビュー対応（2026-09-11）

### 対象と実行環境

レビュー開始時の対象は [`a16b3808`](https://github.com/unitier0214/expense-flow/commit/a16b38083aed0d1b417e2c6b015743dab50e4be4) だった。既存の変更を保護した検証用ブランチ `final-review-fix-20260911` で修正を行い、Java 21・Spring Boot 4.1.1・PostgreSQL 17.11・Dockerを備えたGitHub Actionsで検証した。

- コード・V2・回帰テストの検証コミット：[`6a7c357b`](https://github.com/unitier0214/expense-flow/commit/6a7c357b50582e6120bf5be581a8515b2e12e93f)
- 上記コードのCI：[run 34610990791](https://github.com/unitier0214/expense-flow/actions/runs/34610990791)。test／verify／Compose smoke成功。
- ブラウザ・脆弱性検査を含む最終検証コミット：[`7bb01ba1`](https://github.com/unitier0214/expense-flow/commit/7bb01ba1d56f879a1d86f417b1ae222266c7a365)
- 最終CI：[run 34612201340](https://github.com/unitier0214/expense-flow/actions/runs/34612201340)。test、verify、Compose設定、Compose smoke、Chromium smoke、Trivy検査の全job成功。

### 最終結果

| 検証 | 結果 |
|---|---|
| `./mvnw --batch-mode test` | 成功。52テスト、失敗0・エラー0・スキップ0（Expense 20、Approval 11、CSV 7、Security 12、Demo initializer 1、V2移行 1） |
| `./mvnw --batch-mode verify` | 成功。52テストとパッケージングが完了 |
| `docker compose config --quiet` | 成功 |
| Flyway V1／V2 + PostgreSQL 17 | 成功。V1を変更せず、V2のseed marker作成と旧正規件名の重複バックフィルを実DBで確認 |
| デモ再初期化 | 成功。同名申請の追加、seed件名変更、seed DRAFT削除、initializer複数回実行後も申請45件・履歴101件・marker45件を維持し、削除行を復活させないことを確認 |
| 全角空白入力 | 成功。作成件名、編集用途、差戻し理由を項目別400とし、状態・version・履歴を変更しないことを確認。Service／Entityの`strip()`判定も確認 |
| Compose smoke | 成功。既存の作成→編集→申請→差戻し→修正・再申請→別承認者承認→詳細・履歴→ログアウト、検索条件付きCSV、保護URL拒否、DB永続化を確認 |
| 実ブラウザ | 成功。CI上のChromium＋Playwrightでログイン、作成、編集エラーから同一IDへ復帰、訂正、申請、差戻し、再申請、別承認者の承認、ログアウト後の保護URL拒否を実DOM操作で確認 |
| ブラウザ画面 | 成功。スクリーンショットを [browser artifact](https://github.com/unitier0214/expense-flow/actions/runs/34612201340/artifacts/10268903974) に保存（ログイン、一覧、作成詳細、編集エラー、編集後、差戻し、承認済み、ログイン復帰） |
| 依存脆弱性検査 | 旧filesystem scanは`pom.xml`だけが対象だったため根拠にしない。追加確認でSBOMを検査し、詳細は下記「依存脆弱性検査の追加確認」に記録 |

### 最終レビュー指摘への対応

- `DemoDataInitializer`は件名検索をやめ、`demo_seed_entries`の`expense-01`〜`expense-45`を投入済み判定に使う。マーカーは`expense_requests`へのFKを持たず、seed申請の件名変更・削除後も残る。V2は旧実装の正規件名に一致する行をseedごとに一つだけバックフィルする。
- 件名・用途・差戻し理由をServiceとEntityで`String.strip()`へ揃え、全角スペースだけの値がEntityまで到達して500になる経路をなくした。Serviceでは項目別`ExpenseInputException`／`ApprovalInputException`を先に返し、汎用`IllegalArgumentException`の一律400化は行っていない。
- 既存の同時更新テストは独立トランザクション、version読み取り同期、flush／commit、例外原因、最終値・version・履歴を検証する状態を維持した。最終コードでは20件のExpenseテスト、11件のApprovalテストが通過した。

### 依存脆弱性検査の経緯と未解決事項

- 最初にOWASP Dependency-Check Maven Plugin 13.0.0を実行したが、[run 34611756067](https://github.com/unitier0214/expense-flow/actions/runs/34611756067)では現行NVD APIが空のAPIキーを拒否し、NVDデータがないため分析を完了できなかった。これは脆弱性0件の結果として扱っていない。
- 代替として、`./mvnw --batch-mode -DskipTests package`後にCycloneDX Maven Plugin 2.9.1でcompile/runtime（推移的依存を含む）のSBOMを生成し、次のTrivy走査を実行した。

  ```bash
  docker run --rm -v "$GITHUB_WORKSPACE:/src" -w /src \
    aquasec/trivy:0.58.2 \
    sbom --scanners vuln --format json --output trivy-report.json \
    --exit-code 0 --no-progress target/bom.json
  ```

- 旧[run 34614730290](https://github.com/unitier0214/expense-flow/actions/runs/34614730290)のTrivy JSONは`ArtifactType=filesystem`で、`Results`に`Target=pom.xml`、`Type=pom`の1件しかなく、生成jarの走査を裏付けなかった。この記録を受け、上記のSBOM検査へ修正した。
- OWASPの試行はNVD APIキーなしのため解析を完了できず、依存解析失敗を脆弱性0件とは扱っていない。

### 依存脆弱性検査の追加確認（2026-09-11）

開始時に確認したmainは [`017f4e02`](https://github.com/unitier0214/expense-flow/commit/017f4e02729894d1b7d3f0fbe8ad0bf453e8336b) である。検証対象コミットは [`19b6836d`](https://github.com/unitier0214/expense-flow/commit/19b6836dd000d8c605e6bb6d7986010196c7a17e)、CIは[run 34633201713](https://github.com/unitier0214/expense-flow/actions/runs/34633201713)である。修正後のdependency-scan jobは成功し、同じrunで52テスト、verify、Compose設定・smoke、Chromium＋Playwright smokeも成功した。

| 項目 | 実測結果 |
|---|---|
| Maven／SBOM | `./mvnw --batch-mode -DskipTests package`成功。CycloneDX 1.6、コンポーネント105件 |
| Spring Boot生成jar | `target/expense-flow-0.1.0-SNAPSHOT.jar`。`BOOT-INF/lib`の実行時ライブラリ92本 |
| Trivy解析対象 | `ArtifactName=target/bom.json`、`ArtifactType=cyclonedx`、`Target=Java`、`Class=lang-pkgs`、`Type=jar` |
| パッケージ記録 | `target/runtime-dependency-inventory.json`に105件の名前・バージョン・scope・PURL、`runtime-dependencies.txt`にjar内92本、`runtime-dependency-tree.txt`にruntime依存ツリー |
| 代表パッケージ | `spring-boot` 4.1.1、`tomcat-embed-core` 11.0.25、`postgresql` 42.7.13 |
| 検出数 | 0件。severity counts `{}` |

成果物は[expenseflow-dependency-scan artifact](https://github.com/unitier0214/expense-flow/actions/runs/34633201713/artifacts/10277196756)である。`trivy-report.json`の`Results`が空ではなくJava jar解析結果を含むこと、inventoryが空でないことをCIスクリプトでも検証しているため、解析対象なしや依存解析失敗を0件として成功扱いしない。

初回のSBOM検査（[run 34632765972](https://github.com/unitier0214/expense-flow/actions/runs/34632765972)、[artifact](https://github.com/unitier0214/expense-flow/actions/runs/34632765972/artifacts/10277755317)）では、`tomcat-embed-core` 11.0.24に次の3件を検出した。検査後にSpring Boot 4.1.1を維持し、`pom.xml`の`tomcat.version`だけを11.0.25へ更新した。

| 脆弱性 | パッケージ | 検出／修正版 | 影響の要約 |
|---|---|---|---|
| CVE-2026-65182 | `tomcat-embed-core` | 11.0.24 → 11.0.25 | 長いpath constraintの順序によるsecurity constraint bypass |
| CVE-2026-65905 | `tomcat-embed-core` | 11.0.24 → 11.0.25 | DIGEST認証で限定的なreplayが可能 |
| CVE-2026-68525 | `tomcat-embed-core` | 11.0.24 → 11.0.25 | FORM認証でmethod-specific constraintを迂回する可能性 |

影響範囲と修正版は[Apache Tomcat 11の公式セキュリティ情報](https://tomcat.apache.org/security-11.html)と照合した。修正後の再検査では3件は再現せず、無関係な依存の一括アップグレードは行っていない。Trivyの結果は検査時点の脆弱性DBに基づくため、将来の新規脆弱性やNVD APIキー付きOWASP検査を保証するものではない。

### ブラウザとローカル環境

- ローカルでは標準Javaが17.0.20、Docker CLIがなく、`./mvnw --batch-mode compile`はMaven Central (`repo.maven.apache.org`) の名前解決失敗で依存取得前に終了した。ローカルのtest／verify／Testcontainers／Compose／アプリ起動は未実行である。
- ローカルの実ブラウザ操作・目視は未実行。代わりに最終CIで実Chromiumを起動し、上記の操作とスクリーンショット保存を完了した。CIのcurl smokeとPlaywrightブラウザ確認は区別している。
- V2適用前に旧実装上ですでに改名・削除されたseed申請は、V1にseed識別子がないため完全には推測できない。V2は既存の正規件名を安全にバックフィルし、V2適用後は永続markerで再作成を防ぐ設計とした。

テストの無効化・スキップ、H2やモックDBへの置換、Java 17への変更、CSRF無効化、V1の書き換え、実運用秘密情報のコミットは行っていない。

