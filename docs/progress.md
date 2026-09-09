# Progress

## フェーズ2：社員の申請機能

### 実装内容

- `/expenses` を本人の申請一覧へ置き換え、状態・分類・利用日範囲・件名検索、20件ページングを実装。
- 新規作成、詳細・履歴、編集、下書き削除、申請・再申請の画面とPOSTルートを実装。経費変更の成功レスポンスは303とした。
- Controller / Form DTO / Service / Repository / Entityを分離し、申請者・部署・状態・日時は認証ユーザーとサーバーのClockから決定。
- DRAFT / RETURNEDだけ編集、DRAFTだけ削除、DRAFT / RETURNEDからSUBMITTEDへ遷移。承認・差戻しの実操作は追加していない。
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

## フェーズ3への申し送り

- 同部署・本人以外・SUBMITTEDに固定した承認待ち一覧を追加する。
- APPROVERの承認、理由付き差戻し、自己承認防止、承認・差戻しの競合と履歴を実装する。
- RETURNEDの差戻し理由表示を確認し、現フェーズの再申請処理と接続する。
- 本人一覧と同じ認可・検索条件を共有するCSV出力を、数式対策・出力上限込みで追加する。
- EMPLOYEEとAPPROVERの2ロールを維持し、管理者ロールや実認証情報を追加しない。
