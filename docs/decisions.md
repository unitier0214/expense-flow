# Decisions

## 2026-09-09：基盤バージョン

- Java 21を採用する。設計書の指定であり、Spring Boot 4.1.1の公式システム要件はJava 17以上・Java 26以下のため、互換範囲内である。
- Spring Boot 4.1.1を固定する。公式リファレンスでStableとして公開されているバージョンを使用し、previewやlatestタグには依存しない。
- Spring Boot 4.1.1の公式システム要件ではMaven 3.6.3以上がサポート対象である。
- Spring SecurityのCSRF保護は無効化しない。Spring Securityの公式リファレンスに従い、フォームPOSTにはトークンを含める。

参照：

- https://docs.spring.io/spring-boot/system-requirements.html
- https://docs.spring.io/spring-boot/reference/index.html
- https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html

## 2026-09-09：フェーズ1の範囲

- 申請・承認の本体機能は後続フェーズで実装する。ログイン後の `/expenses` は、未実装範囲を明示する案内画面にする。
- 全テーブルはフェーズ1のFlyway初回マイグレーションで作成する。後続フェーズでは既存テーブルを破壊せず、追加マイグレーションで進める。
- demoデータはJavaの `ApplicationRunner` でdemoプロファイル時だけ冪等投入する。通常プロファイルではユーザーを自動生成しない。
- テストはTestcontainersのPostgreSQLを前提にする。Dockerが利用できない環境では、テストコードを残し、実行不能の理由を `docs/test-results.md` に記録する。

## 2026-09-09：Spring Boot 4.1.1のテスト・DB依存関係

- Spring Boot 4.1.1とJava 21は維持する。Testcontainers 2.0.2では、存在する成果物名である`testcontainers-junit-jupiter`と`testcontainers-postgresql`を使用する。
- Spring Boot 4のテスト分割に合わせ、`spring-boot-starter-webmvc-test`と`org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`を使用する。
- Flywayの自動構成に必要な`spring-boot-starter-flyway`を追加する。Flywayの適用先は引き続きPostgreSQLのみとし、H2へ置換しない。

## 2026-09-09：フェーズ1の検証方法

- ローカル環境にJava 21とDockerがないため、Java 21・PostgreSQL 17・Dockerを提供するGitHub Actionsを実行環境とした。依存取得ができないローカルの失敗を成功扱いにはしていない。
- Compose smoke testでは、HTMLからCSRFトークンを取得してログインとPOSTログアウトを行い、DBをボリューム削除なしで再作成して永続化を検証する。
- フェーズ1時点のログイン後プレースホルダー画面では、未導入のThymeleaf Security dialectに依存せず、認証済みユーザー名をコントローラーからモデルへ渡した。フェーズ2でこの画面を申請一覧へ置き換えた。

## 2026-09-09：フェーズ2の申請機能

- 現行のFlyway V1で申請、状態、version、履歴に必要なカラムと制約が揃っているため、V1を変更せずV2も追加しない。将来スキーマ変更が必要になった場合だけ追加マイグレーションで対応する。
- Controllerは`ExpenseForm`だけをバインドし、認証済みusernameから`CurrentUserService`でDBのユーザー・部署・roleを解決する。申請者、部署、状態、日時をPOSTパラメータから採用しない。
- ExpenseRequestの状態変更は`updateDetails`と`submit`に限定し、DRAFT／RETURNEDの編集、DRAFTの削除、DRAFT／RETURNEDからSUBMITTEDへの申請だけを公開した。承認・差戻し操作とCSVはこのフェーズに含めない。
- `Clock`を注入し、操作時刻はInstantで保存し、利用日の当日判定と表示はAsia/Tokyoで統一する。JPAのライフサイクルで`Instant.now()`を呼び出さない。
- 金額は入力文字列をtrimして`[0-9]+`だけを許可し、BigDecimalへ変換する。NUMERIC(12,0)の丸めを受け入れず、範囲・整数値をサービスとドメインの双方で検証する。入力エラー時は元の文字列をフォームへ戻す。
- 一覧の検索条件は本人IDを必須条件にしたJPAクエリで絞り、更新日時降順・ID降順で20件ずつ取得する。PostgreSQLのnullパラメータ型推論を避けるため、件名検索あり／なしのクエリを分け、任意条件は対象列と`coalesce`して型を確定させる。`%`、`_`、バックスラッシュはLIKEのワイルドカードとして解釈しない。
- 変更操作ではnullable `Long version`の欠落・形式不正を400、古いversion・不正遷移・楽観ロックを409とする。本人以外は状態判定より前に404とし、存在しないIDと同じ応答にする。
- 経費変更とCREATE／UPDATE／SUBMIT履歴は同一`@Transactional`で保存する。正常な経費POSTは303、Spring Securityのログイン・ログアウトリダイレクトは既存どおり302とする。
- ユーザーロールは設計書どおりEMPLOYEEとAPPROVERの2種類だけとする。既存の現状報告にあった「管理者」は誤記として訂正し、管理者ロールは追加しない。
- READMEに記載するdemoのusername/passwordは、demoプロファイル専用の架空fixtureであり、公開してよい検証用固定値として扱う。実運用の秘密情報や`.env`はコミットしない。

## 2026-09-09：フェーズ2レビュー修正

- 編集POSTの対象は常に`@PathVariable id`とし、入力エラー・version型変換エラーの再表示でも`/expenses/{id}/edit`と元申請へのキャンセル先をサーバー側で組み立てる。フォームから送信された`id`は更新対象と再表示先の決定に使わない。
- version型変換エラーはhidden項目だけで終わらせず、編集画面に項目別の日本語エラーを表示する。Serviceの認可とversion検証は従来どおり維持する。
- 同時更新テストは`TransactionTemplate`で独立したPostgreSQLトランザクションを作り、両者がversion 0を読み終えた後に更新する`CyclicBarrier`を置く。Futureとbarrierにタイムアウトを設け、成功1件・楽観ロック例外を含む失敗1件、勝者の内容・version・履歴を確認する。サービスの事前version比較は別のHTTPテストで検証し、DBの`@Version`検知と混同しない。

## 2026-09-09：フェーズ3の承認・差戻し

- 承認待ち一覧の検索条件を`department_id`、本人以外、`SUBMITTED`へ固定し、承認操作の対象認可も同じService判定を通す。社員の承認操作は403、承認者から見えない自己処理・他部署・DRAFTは404、同部署で見えるがSUBMITTEDではない対象の操作は409とした。
- APPROVERの承認・差戻しは、対象の認可、version比較、状態遷移、履歴保存を1つの`@Transactional`メソッドに置いた。コメントのtrim・長さ検証を状態変更前に行い、履歴保存に失敗した場合は状態とversionもロールバックする。
- 承認コメントは任意、差戻し理由は必須とし、画面のボタン非表示だけで権限を守らず、検索クエリ・Service・HTTPテストの3箇所で確認する。自分の申請を承認できる別の管理者ロールは追加せず、EMPLOYEEとAPPROVERだけを維持する。
- RETURNEDからの再申請は既存の本人操作を再利用し、`submitted_at`をClockの現在時刻へ更新してSUBMIT履歴を追加する。APPROVE／RETURNには追加マイグレーションを作らず、既存V1の状態・履歴制約を利用する。

## 2026-09-09：フェーズ4のデモ・画面仕上げ

- demo申請はタイトル`デモ申請-01`〜`デモ申請-45`を冪等キーとして扱う。営業部23件、開発部22件へ分け、番号の剰余でDRAFT／SUBMITTED／RETURNED／APPROVEDを分散させる。初期状態とCREATE／SUBMIT／RETURN／APPROVE履歴を同じトランザクションで作る。
- 既存の同名申請は内容・状態・履歴を更新しない。初回投入後の再起動でデモデータを上書きしない要件を優先し、既存データの修復機能は追加しない。通常プロファイルには`@Profile("demo")`で初期化Beanを登録しない。
- 承認待ち一覧にも状態ラベルを表示する。画面上で「承認待ち」という文脈だけに依存せず、対象申請のSUBMITTED状態をHTMLから確認できるようにした。
- Compose smokeはcurlを用いたHTTP検証として実装する。各GETのHTMLからCSRFとversionを取得し、件名・状態ラベル・差戻し理由・承認コメント・履歴を対象申請の値として確認する。実ブラウザ操作の代替とは記録しない。
- 小さな画面幅では表を横スクロールさせ、フォームの主要ボタンはタップしやすい最小高さを確保する。設計外のダッシュボードや機能は追加しない。
- Phase4の必須機能完成境界は [`f7182a10`](https://github.com/unitier0214/expense-flow/commit/f7182a10c71e23e3839b22719aac88f1c365e91a) とする。CSVはこの境界の後に別コミットとして実装する。
