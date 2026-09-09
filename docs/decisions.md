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

