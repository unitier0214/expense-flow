# Test Cases

フェーズ1の既存テストの意味を維持した。フェーズ2レビュー修正後は32テスト、フェーズ3ではApprovalIntegrationTest 10件を追加し、合計42テストとなった。フェーズ3のCI実行は失敗0・エラー0・スキップ0だった。CSVはフェーズ5として扱う。

| ID | フェーズ2・3での対応範囲 | 手段・主な確認 |
|---|---|---|
| T01 | 対応済み | Testcontainers PostgreSQL + MockMvcでログイン、未認証リダイレクト、ログイン／ログアウトCSRF拒否、ログアウト後の保護URL再認証を確認 |
| T02 | 対応済み | 件名・用途のtrimと上限、分類、JST当日以前、金額の最小／最大・範囲外・小数・指数・カンマ・非数値、元入力保持を確認 |
| T03 | 対応済み | 本人の作成→編集→申請、RETURNED fixtureの編集→再申請、編集入力エラーから元IDへ復帰、DB状態・submitted_at・CREATE/UPDATE/SUBMIT履歴を確認 |
| T04 | 対応済み | 同部署の別APPROVERによる承認、任意コメント、承認後の再承認・編集拒否をHTTPとDB履歴で確認 |
| T05 | 対応済み | 理由なし・501文字差戻しの400、trim後理由の保存、RETURNEDの本人編集→再申請を確認 |
| T06 | 対応済み | 社員の承認操作403、自己承認・他部署・DRAFT対象404、同部署承認者のSUBMITTED詳細・履歴閲覧を確認 |
| T07 | 対応済み | URL／POST ID改変、`applicant_id`・`department_id`・`status`等の余計なパラメータを無視し、所有者・部署・状態が変わらないことを確認 |
| T08 | 対応済み | 承認version欠落／型不正／古い値、不正遷移、承認対承認・承認対差戻しを独立トランザクションで競合させ、成功1・楽観ロック競合1と勝者の履歴だけを確認 |
| T09 | 対応済み | PostgreSQLトリガーでAPPROVE／RETURN履歴保存を失敗させ、状態・version・履歴の双方がロールバックすることを確認 |
| T10 | 対応済み | 本人限定の状態・分類・利用日期間・件名検索、両端含み、`%`／`_`のリテラル扱い、条件保持、0件、21件以上の20件ページング、不正検索400を確認 |
| T11 | 対応済み | 件名・用途を一覧・詳細でHTMLエスケープ表示し、タグが実行されないことを確認 |
| T12 | 対応済み | 新規PostgreSQLへのFlyway V1、Java 21のtest／verify、ComposeのDB readiness・healthcheck・ログインから永続化までを確認 |
| T13 | フェーズ5対象 | CSV出力、認可付き全件抽出、引用符・改行・数式対策、出力上限をフェーズ5で追加検証する |

自動テストはRepositoryのモックだけで済ませず、TestcontainersのPostgreSQL 17とMockMvcを使用する。RETURNED／APPROVEDの状態fixtureはテスト側のJDBCで用意し、実アプリケーションに状態変更の裏口は追加していない。

フェーズ3の承認テストは `src/test/java/jp/example/expenseflow/feature/expense/ApprovalIntegrationTest.java` に置き、承認待ち一覧、ロール・部署・自己処理の認可、理由・version入力、状態遷移、履歴ロールバック、PostgreSQLの実楽観ロックを確認する。
