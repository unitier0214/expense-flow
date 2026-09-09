# Test Cases

フェーズ1の既存12テスト（SecurityIntegrationTest 11、DemoDataInitializerIntegrationTest 1）の意味を維持した。フェーズ2では申請機能のHTTP・DB結合テスト18件を追加し、SecurityIntegrationTestにもログアウト後の保護URL再認証確認を1件追加した。最終実行は合計31テストで、失敗0・エラー0・スキップ0だった。

| ID | フェーズ2での対応範囲 | 手段・主な確認 |
|---|---|---|
| T01 | 対応済み | Testcontainers PostgreSQL + MockMvcでログイン、未認証リダイレクト、ログイン／ログアウトCSRF拒否、ログアウト後の保護URL再認証を確認 |
| T02 | 対応済み | 件名・用途のtrimと上限、分類、JST当日以前、金額の最小／最大・範囲外・小数・指数・カンマ・非数値、元入力保持を確認 |
| T03 | 対応済み | 本人の作成→編集→申請、RETURNED fixtureの編集→再申請、DB状態・submitted_at・CREATE/UPDATE/SUBMIT履歴を確認 |
| T04 | 後続 | 承認待ち一覧、承認操作、承認済みfixtureの変更拒否の一部だけ今回確認。実承認操作はフェーズ3へ残す |
| T05 | 後続 | RETURNED fixtureの再申請は今回確認。理由必須の差戻し操作と差戻し後の業務フローはフェーズ3へ残す |
| T06 | 一部対応 | 本人・他社員・同部署APPROVER・他部署ユーザーの詳細／履歴／編集／削除／申請権限をHTTPで確認。承認操作はフェーズ3へ残す |
| T07 | 対応済み | URL／POST ID改変、`applicant_id`・`department_id`・`status`等の余計なパラメータを無視し、所有者・部署・状態が変わらないことを確認 |
| T08 | 一部対応 | 編集・削除・申請のversion欠落／型不正／古い値、DRAFT削除競合、同時編集を確認。承認競合はフェーズ3へ残す |
| T09 | 対応済み | PostgreSQLトリガーでUPDATE履歴保存を失敗させ、経費変更と履歴の双方がロールバックすることを確認 |
| T10 | 対応済み | 本人限定の状態・分類・利用日期間・件名検索、両端含み、`%`／`_`のリテラル扱い、条件保持、0件、21件以上の20件ページング、不正検索400を確認 |
| T11 | 対応済み | 件名・用途を一覧・詳細でHTMLエスケープ表示し、タグが実行されないことを確認 |
| T12 | 対応済み | 新規PostgreSQLへのFlyway V1、Java 21のtest／verify、ComposeのDB readiness・healthcheck・ログインから永続化までを確認 |
| T13 | 後続 | CSV出力、認可付き全件抽出、引用符・改行・数式対策、出力上限をフェーズ3以降へ残す |

自動テストはRepositoryのモックだけで済ませず、TestcontainersのPostgreSQL 17とMockMvcを使用する。RETURNED／APPROVEDの状態fixtureはテスト側のJDBCで用意し、実アプリケーションに状態変更の裏口は追加していない。
