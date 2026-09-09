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
