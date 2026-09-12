# Progress

## 2026-09-12 最終総合レビュー修正

R1〜R6を修正。実装検証対象は`b9ca840a6d29692aa4bca281c9ed1f0d527f1ace`、[CI run 34681842105](https://github.com/unitier0214/expense-flow/actions/runs/34681842105)はsuccess。test／verify各57件、失敗・エラー・スキップ0。Compose、Chromium、Maven起動、SBOM検査も成功し、掲載画像5枚を目視した。画像掲載・結果追記後のCIと未確認範囲は [最終修正報告](final-review-2026-09-12.md) と [PR #1](https://github.com/unitier0214/expense-flow/pull/1) を参照。

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

## フェーズ4：アプリと説明資料の仕上げ（完了）

- demoプロファイルへ営業部23件・開発部22件、合計45件の申請を状態分散（DRAFT／SUBMITTED／RETURNED／APPROVED）して投入し、CREATEから各状態に至る履歴も整合させた。
- `DemoDataInitializerIntegrationTest`で初回投入と再実行を確認し、既存件数・既存の編集内容・既存履歴を上書きしないことを検証した。通常プロファイルでは初期化しない。
- 社員の作成→編集→申請→同部署承認者の差戻し→本人の修正・再申請→別承認者の承認をCompose smokeへ追加した。各フォームからCSRFとversionをHTML解析で取得し、固定値に依存していない。
- 承認待ち一覧へ状態ラベルを追加し、詳細の最終状態・差戻し理由・承認コメント・履歴を対象申請の内容として確認するようsmokeを整えた。
- 白背景・濃紺ナビ、共通ヘッダー、入力エラーの`aria-describedby`、フォーカス表示、スマートフォン幅のフォーム操作を整え、`docs/learning-guide.md`を追加した。
- Phase4の実装・資料検証対象は [`f7182a10`](https://github.com/unitier0214/expense-flow/commit/f7182a10c71e23e3839b22719aac88f1c365e91a)。[CI run 34402776016](https://github.com/unitier0214/expense-flow/actions/runs/34402776016)でJava 21のtest／verify、Compose設定、DB付きsmokeをすべて成功させた。

### フェーズ4検証時の修正履歴

- [run 34400373220](https://github.com/unitier0214/expense-flow/actions/runs/34400373220)：失敗。Compose smokeが承認者ログイン後に停止したため、ログインのLocationと承認画面のHTTP statusを出力して切り分けた。
- [run 34401685105](https://github.com/unitier0214/expense-flow/actions/runs/34401685105)：失敗。承認待ち一覧に対象の状態ラベルがなく、対象申請の状態確認が成立していなかったため、一覧表示を修正した。
- [run 34402776016](https://github.com/unitier0214/expense-flow/actions/runs/34402776016)：成功。42テスト、失敗0・エラー0・スキップ0。Compose smokeは作成から承認、ログアウト後の保護URL拒否、DB永続化まで完了した。

## フェーズ5：CSV出力（完了）

- フェーズ4の必須機能完成コミット [`f7182a10`](https://github.com/unitier0214/expense-flow/commit/f7182a10c71e23e3839b22719aac88f1c365e91a) の後に、CSVを独立した追加機能として実装した。
- `GET /expenses/export.csv`を本人一覧と同じ認可・検索条件・`updated_at DESC, id DESC`で実装し、ページングを外した1,001件取得で上限超過を検出する。
- UTF-8 BOM、日本語ヘッダー・状態ラベル・JST日時、固定Content-Disposition、RFC 4180相当の引用、CSV数式対策を実装した。
- 一覧画面へ検索条件を保持するCSVボタンを追加し、T13とT01〜T12を回帰検証した。
- CSV実装対象コミットは [`41f90066`](https://github.com/unitier0214/expense-flow/commit/41f90066a190dc8f0f78faba1849f4b044b19722)、CIは [run 34406014385](https://github.com/unitier0214/expense-flow/actions/runs/34406014385)。49テスト、失敗0・エラー0・スキップ0。`verify`、Compose設定、拡張Compose smokeも成功した。

### 完成時点の申し送り

- フェーズ1〜5の実装範囲はコード・Testcontainers／MockMvcテスト・CI smoke・資料へ反映済み。承認・差戻しの実操作とCSVまでが完成範囲である。
- 実ブラウザの目視とスクリーンショットは、作業環境にDocker／起動アプリ／ブラウザがないため未実施。CIのcurl HTTP検証とは区別して報告する。
- 新規デプロイ、実運用認証情報の投入、設計外の追加機能は行っていない。次の作業はAstraの最終レビューと、必要なら指摘対応である。

### ローカル検証の制約

- ローカルの標準Javaは17.0.20で、Java 21を明示したMaven実行もMaven Centralの名前解決失敗により依存取得前に終了した。
- Docker CLIがないため、ローカルTestcontainers、Compose、アプリ起動、実ブラウザ目視、スクリーンショット取得は未実行。GitHub ActionsではJava 21・PostgreSQL 17.11・Dockerを使ったHTTP smokeを実行したが、curl検証をブラウザ確認とは呼んでいない。
- テストの無効化・スキップ、H2やモックDBへの置換は行っていない。

## 最終レビュー対応（2026-09-11）

レビュー対象 [`a16b3808`](https://github.com/unitier0214/expense-flow/commit/a16b38083aed0d1b417e2c6b015743dab50e4be4) の状態を起点に、既存のフェーズ1〜5の実装を保護して仕上げ対応を行った。

- 件名依存だった`DemoDataInitializer`の投入済み判定を、V2で追加した`demo_seed_entries`の`expense-01`〜`expense-45`へ移した。V2は旧正規件名をseedへ一度だけバックフィルし、マーカーは申請へのFKを持たないため、件名変更・同名追加・seed下書き削除後も再起動で復活・増殖しない。
- `ExpenseService`、`ExpenseRequest`、`ExpenseEvent`の空白処理を`String.strip()`へ揃え、全角スペースだけの件名・用途・差戻し理由を項目別400で拒否する回帰テストを追加した。入力不備を一律例外変換で処理する実装にはしていない。
- `DemoDataInitializerIntegrationTest`を再初期化・同名・改名・削除まで拡張し、`DemoSeedMigrationIntegrationTest`で既存DBのV1→V2バックフィルを実PostgreSQLで検証した。
- 実ブラウザ確認用にCIへChromium＋Playwright smokeを追加した。編集エラーから同一申請へ復帰する画面を含む主要フローを操作し、スクリーンショットをActions artifactへ保存する。
- 依存脆弱性検査はCIの`dependency-scan` jobでSpring Boot実行jarを生成し、compile/runtime（推移的依存を含む）のCycloneDX SBOMをTrivy 0.58.2で解析する。生成jarの`BOOT-INF/lib`一覧と、名前・バージョンを含む依存inventoryをartifactへ保存し、解析対象や依存一覧が欠けた場合は成功扱いにしない。OWASP Dependency-Check 13.0.0はNVD APIキーなしでデータ取得できず、最終判定に流用せず未解決事項として記録する。

コード・マイグレーションの検証対象は [`6a7c357b`](https://github.com/unitier0214/expense-flow/commit/6a7c357b50582e6120bf5be581a8515b2e12e93f)、CI [run 34610990791](https://github.com/unitier0214/expense-flow/actions/runs/34610990791)でtest／verify／Compose smokeが成功した。ブラウザとTrivyを含む最終CIは、結果確定後にこの資料と最終引き継ぎ資料へ追記する。

ローカルの標準Javaは17.0.20、Docker CLIは未導入であり、ローカルMaven（依存取得先の名前解決失敗）、Testcontainers、Compose、アプリ起動、実ブラウザ目視は引き続き未実行。CIではJava 21・PostgreSQL 17・Dockerを使用し、Playwrightによる実ブラウザ操作とスクリーンショット取得まで実行した。

### 最終レビュー検証結果の確定

- ブラウザ・Trivyを含む最終検証対象は [`7bb01ba1`](https://github.com/unitier0214/expense-flow/commit/7bb01ba1d56f879a1d86f417b1ae222266c7a365)、CI [run 34612201340](https://github.com/unitier0214/expense-flow/actions/runs/34612201340)である。test 52件、verify、Compose設定・smoke、Chromium＋Playwright smoke、Trivy検査がすべて成功した。
- 実ブラウザのスクリーンショットは [Actions artifact](https://github.com/unitier0214/expense-flow/actions/runs/34612201340/artifacts/10268903974) に保存した。旧Trivy filesystem scanのJSONは[run 34614730290のartifact](https://github.com/unitier0214/expense-flow/actions/runs/34614730290/artifacts/10270182691)で、解析対象が`pom.xml`だけだったため、生成jarの依存検査の根拠にはしない。
- OWASP Dependency-CheckはNVD APIキーなしでは現行APIからデータを取得できず、[run 34611756067](https://github.com/unitier0214/expense-flow/actions/runs/34611756067)を脆弱性0件とは扱っていない。代替のTrivy結果も検査時点の結果であり、将来の新規脆弱性やAPIキー付きNVD検査を保証しない。
- V2適用前に改名・削除されたseed申請は、V1にseed識別子がないため完全には推定できない。V2適用後は永続markerを唯一の投入済み判定にする。

### 依存脆弱性検査の追加確認

- 開始時のmainは [`017f4e02`](https://github.com/unitier0214/expense-flow/commit/017f4e02729894d1b7d3f0fbe8ad0bf453e8336b) だった。そこから、旧filesystem scanの実対象を確認し、依存検査だけを修正した。
- 依存検査の修正コミットは [`19b6836d`](https://github.com/unitier0214/expense-flow/commit/19b6836dd000d8c605e6bb6d7986010196c7a17e)。[CI run 34633201713](https://github.com/unitier0214/expense-flow/actions/runs/34633201713)でdependency-scan、52テスト、verify、Compose設定・smoke、Chromium smokeが成功した。
- Trivyの実解析対象は`ArtifactName=target/bom.json`、`ArtifactType=cyclonedx`、`Target=Java`、`Class=lang-pkgs`、`Type=jar`だった。SBOMは105コンポーネント、生成jarは`target/expense-flow-0.1.0-SNAPSHOT.jar`、`BOOT-INF/lib`は92本で、artifactの`target/runtime-dependency-inventory.json`に全105パッケージの名前・バージョン・PURLを保存した。代表例は`spring-boot` 4.1.1、`tomcat-embed-core` 11.0.25、`postgresql` 42.7.13である。
- 初回SBOM検査では`tomcat-embed-core` 11.0.24に3件が検出された。CVE-2026-65182、CVE-2026-65905、CVE-2026-68525はいずれも11.0.25で修正されるため、Tomcatのパッチプロパティだけを更新した。修正後のTrivy結果は脆弱性0件（severity counts `{}`）で、成果物は[dependency scan artifact](https://github.com/unitier0214/expense-flow/actions/runs/34633201713/artifacts/10277196756)である。

