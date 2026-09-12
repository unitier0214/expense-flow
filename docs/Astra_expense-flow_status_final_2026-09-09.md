> 過去時点の記録です。最新の状態は [2026-09-12 最終修正報告](final-review-2026-09-12.md) と [テスト結果](test-results.md) を参照してください。

# ExpenseFlow 最終現状報告（2026-09-09）

## 判定対象

この資料は、ExpenseFlowをフェーズ1〜5まで実装した時点でAstraへ引き継ぐための報告である。最終的な合否判定はAstraに委ねる。この作業結果をAstra確認済みとは記載しない。

> 2026-09-11更新：以下の2026-09-09時点の記録に加え、レビュー対象 [`a16b3808`](https://github.com/unitier0214/expense-flow/commit/a16b38083aed0d1b417e2c6b015743dab50e4be4) への最終レビュー対応を実施した。最新の検証結果は末尾の「最終レビュー対応」を参照する。

- リポジトリ：[unitier0214/expense-flow](https://github.com/unitier0214/expense-flow)
- 設計の正本：[docs/design.md](design.md)
- 開始時のレビュー対象：[`4077b2bd`](https://github.com/unitier0214/expense-flow/commit/4077b2bd34f822f91e961ce2c067fbafa8ea5db9)
- 今回開始時に確認した直近対象：[`4077b2bd`](https://github.com/unitier0214/expense-flow/commit/4077b2bd34f822f91e961ce2c067fbafa8ea5db9)
- CSV追加前の必須機能完成SHA：[`f7182a10`](https://github.com/unitier0214/expense-flow/commit/f7182a10c71e23e3839b22719aac88f1c365e91a)
- CSV追加のコード検証SHA：[`41f90066`](https://github.com/unitier0214/expense-flow/commit/41f90066a190dc8f0f78faba1849f4b044b19722)

## 完成した機能

### フェーズ2：社員の申請

- 本人一覧、状態・分類・利用日期間・件名検索、20件ページング。
- 下書き作成・編集・削除、申請・再申請、詳細・履歴。
- DRAFT／RETURNEDのみ編集、DRAFTのみ削除。作成者・部署・状態・日時はサーバー側で決定。
- 金額の元文字列を検証し、半角整数1〜1,000,000以外を400で拒否。小数・指数・カンマを丸めて保存しない。
- URLの@PathVariableを編集対象の正とし、入力エラー・version型エラー後も元IDのaction／キャンセル先を維持。
- Service認可、CSRF、`@Transactional`、`@Version`、JST日付判定・表示、404／409／500画面。

### フェーズ3：承認・差戻し

- `/approvals` は同部署・本人以外・SUBMITTEDのみ、`submitted_at ASC, id ASC`、20件ページング。
- APPROVERだけが承認・差戻し可能。社員は403、自己処理・他部署・DRAFTは404、古いversion・不正遷移は409。
- 任意の承認コメント（最大500文字）、必須の差戻し理由（trim後1〜500文字）。状態変更とAPPROVE／RETURN履歴を同一トランザクションで保存。
- 差戻し後の本人修正・再申請、submitted_at更新、競合、履歴保存失敗ロールバックを実装・検証。

### フェーズ4：デモと画面

- demoプロファイルで営業部23件・開発部22件、合計45件をDRAFT／SUBMITTED／RETURNED／APPROVEDへ分散して冪等投入。
- CREATE／SUBMIT／RETURN／APPROVE履歴を状態と整合させ、既存データを再起動で上書きしない。
- 白背景・濃紺ナビ、共通ヘッダー、状態ラベル、入力エラー対応、スマートフォン幅の主要操作を整備。
- Compose smokeを作成→編集→申請→差戻し→修正・再申請→承認→詳細／履歴→ログアウト→DB永続化へ拡張。

### フェーズ5：CSV

- `GET /expenses/export.csv`を追加。本人一覧と同じ認可・検索条件・`updated_at DESC, id DESC`で全該当行を出力し、ページングは外した。
- 1,000件までを許可し、1,001件以上は400で絞り込みを案内。0件はBOM付きヘッダーのみ。
- UTF-8 BOM、`ID／件名／分類／利用日／金額／状態／更新日時`、JST表示、固定ファイル名、RFC 4180相当の引用・CRLF、数式対策。
- 一覧のCSVボタンはstatus/category/from/to/qを保持する。

## フェーズ2指摘の修正

- 編集画面の再送信先をフォーム内のidでは決めず、常に`@PathVariable id`から生成した。
- 金額`1.5`、id欠落・改変、version型不正のエラー後も元の編集action／キャンセル先を維持し、訂正後は新規作成でなく元申請をUPDATEする回帰テストを追加した。
- 同時更新テストは独立PostgreSQLトランザクションでversion読み取りを同期し、flush／commitまで行い、成功1件と`ObjectOptimisticLockingFailureException`系競合1件、最終値・version・履歴を確認した。Future／同期にはタイムアウトを設定した。

## テスト・CI

### 対象SHAとCI

| 段階 | 対象SHA | 成功CI |
|---|---|---|
| フェーズ2レビュー修正 | [`ff441f7a`](https://github.com/unitier0214/expense-flow/commit/ff441f7a3fa39782ddbbc54719f72b6bf7f5b4d5) | [run 34394247514](https://github.com/unitier0214/expense-flow/actions/runs/34394247514) |
| フェーズ3承認・差戻し | [`43dcf379`](https://github.com/unitier0214/expense-flow/commit/43dcf3791b6834f540b540a46d3349e2159af7a5) | [run 34397993092](https://github.com/unitier0214/expense-flow/actions/runs/34397993092) |
| CSV追加前の必須機能 | [`f7182a10`](https://github.com/unitier0214/expense-flow/commit/f7182a10c71e23e3839b22719aac88f1c365e91a) | [run 34402776016](https://github.com/unitier0214/expense-flow/actions/runs/34402776016) |
| フェーズ5 CSV | [`41f90066`](https://github.com/unitier0214/expense-flow/commit/41f90066a190dc8f0f78faba1849f4b044b19722) | [run 34406014385](https://github.com/unitier0214/expense-flow/actions/runs/34406014385) |

### 最終CSV CI結果

- `./mvnw --batch-mode test`：49テスト、失敗0・エラー0・スキップ0。内訳はExpense 19、Approval 10、Csv 7、Security 12、Demo initializer 1。
- `./mvnw --batch-mode verify`：成功。
- `docker compose config --quiet`：成功。
- Compose smoke：Java 21・PostgreSQL 17.11・Dockerで、DB readiness、healthcheck、ログイン、社員の作成・編集・申請、差戻し、再申請、承認、対象申請の状態・理由・コメント・履歴、CSV、ログアウト後の保護URL拒否、DB永続化を確認。
- 初回CSV CI [run 34405496732](https://github.com/unitier0214/expense-flow/actions/runs/34405496732) は、テスト専用固定ClockのBean重複で失敗した。`@Primary`を付ける修正後に上記CIが成功した。失敗を隠すためのテスト削除・スキップはしていない。

### 主要仕様との対応

T01〜T12はフェーズ1〜4の回帰テスト、T13はCSV追加テストとして対応済み。詳細な対応表は [docs/test-cases.md](test-cases.md)、実行結果は [docs/test-results.md](test-results.md) を参照する。

## 実行できなかった検証と理由

- ローカルのJava 21でのMaven実行：環境の標準Javaは17.0.20で、Java 21を指定した実行もMaven Central (`repo.maven.apache.org`) の名前解決に失敗し、依存取得前に終了した。
- ローカルTestcontainers／Compose／アプリ起動：Docker CLIがインストールされていない。
- 実ブラウザの操作、画面の目視、実スクリーンショット：上記によりアプリを起動できず、ブラウザ環境も利用できない。CIのcurlによるHTTP・HTML検証をブラウザ確認とは呼んでいない。

仕様を緩める対応はしていない。Java 17への変更、H2／モックDB置換、CSRF無効化、テストの無効化・スキップ、実認証情報の投入は行っていない。

## 起動・ログイン・3分デモ

```bash
cp .env.example .env
# .envのPOSTGRES_PASSWORDを設定
SPRING_PROFILES_ACTIVE=demo docker compose up --build
```

ブラウザで `http://localhost:8080/login` を開き、README記載のdemo公開fixtureでログインする。社員で作成→編集→申請、承認者1で理由付き差戻し、社員で修正・再申請、承認者2で承認、詳細・履歴とCSVを確認し、POSTログアウト後に保護URLがログインへ戻ることを示す。具体的な操作順は [docs/demo-script.md](demo-script.md) にある。

## 申し送り

- 実装・CI・資料はフェーズ5まで進めた。次はAstraによる最終レビューであり、Astraの承認や合否をこの報告から先取りしない。
- 設計の正本は`docs/design.md`、学習・説明は`docs/learning-guide.md`、判断理由は`docs/decisions.md`に記録した。
- 新規デプロイ、課金サービス契約、外部連絡、実運用秘密情報の追加は今回の範囲外である。

## 最終レビュー対応（2026-09-11）

### 対応内容

- 件名検索に依存していた`DemoDataInitializer`の投入済み判定を、V2で追加した`demo_seed_entries`の永続キーへ移した。旧正規件名はseedごとに一つだけバックフィルし、マーカーに申請へのFKを持たせないため、同名申請の追加、seed件名の変更、seed下書きの削除後も再起動で復活・増殖しない。
- 件名・用途・差戻し理由のService／Entity間の空白判定を`String.strip()`へ統一し、全角スペースだけの入力を項目別メッセージ付き400で拒否する回帰テストを追加した。汎用`IllegalArgumentException`の一律400変換は行っていない。
- CIにChromium＋Playwrightの実DOM smokeを追加し、主要フロー、編集入力エラーから同一申請への復帰、ログアウト後の保護URL拒否、スクリーンショット取得を実行した。
- 依存脆弱性検査はTrivy 0.58.2を採用し、検査結果をJSON artifactへ保存した。OWASP Dependency-CheckはNVD APIキーなしで現行APIからデータ取得できず、0件の根拠にはしていない。

### 対象SHAと検証結果

- 今回の開始時main：[`017f4e02`](https://github.com/unitier0214/expense-flow/commit/017f4e02729894d1b7d3f0fbe8ad0bf453e8336b)。このSHAの旧filesystem scanは`pom.xml`だけが対象だった。
- コード・V2・回帰テスト：[`6a7c357b`](https://github.com/unitier0214/expense-flow/commit/6a7c357b50582e6120bf5be581a8515b2e12e93f)。[run 34610990791](https://github.com/unitier0214/expense-flow/actions/runs/34610990791)でtest／verify／Compose smoke成功。
- ブラウザを含む前回の最終検証：[`7bb01ba1`](https://github.com/unitier0214/expense-flow/commit/7bb01ba1d56f879a1d86f417b1ae222266c7a365)。この時点のfilesystem scanは後の確認で`pom.xml`だけが対象と判明したため、生成jarの依存検査の根拠にはしない。
- 依存検査を修正した検証コミット：[`19b6836d`](https://github.com/unitier0214/expense-flow/commit/19b6836dd000d8c605e6bb6d7986010196c7a17e)。[run 34633201713](https://github.com/unitier0214/expense-flow/actions/runs/34633201713)でtest 52件、verify、Compose設定・smoke、Chromium smoke、SBOMを対象にしたTrivy検査がすべて成功した。
- ブラウザのスクリーンショット：[Actions artifact](https://github.com/unitier0214/expense-flow/actions/runs/34612201340/artifacts/10268903974)。依存検査のJSON・SBOM・依存一覧：[dependency scan artifact](https://github.com/unitier0214/expense-flow/actions/runs/34633201713/artifacts/10277196756)。Trivyの実解析対象は`target/bom.json`（`ArtifactType=cyclonedx`、`Target=Java`、`Type=jar`）、SBOM 105件、生成jarの`BOOT-INF/lib` 92本、修正後の検出0件だった。

### 未確認事項・既知の制約

- ローカルはJava 17.0.20、Docker CLIなし、Maven Centralの名前解決不可のため、ローカルtest／verify／Testcontainers／Compose／アプリ起動／実ブラウザ目視は未実行。実ブラウザ操作はCI上のChromiumで実行済みであり、curlによるHTTP smokeとは区別している。
- OWASP Dependency-CheckのNVD APIキー付き再検査は未実行。代替のTrivyは検査時点のDBで0件だったが、将来の新規脆弱性やNVD APIキー付き検査を保証しない。
- V2適用前に旧実装上ですでに改名・削除されたseed申請は、V1にseed識別子がないため完全には推定できない。V2適用後は永続markerを唯一の投入済み判定にする。

最終的な合否判定はAstraに委ねる。Astraによる確認済み・合格済みとは記載していない。

