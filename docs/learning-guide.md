# ExpenseFlow 学習ガイド

この資料は、Javaを学び始めて約2ヶ月の人が、ExpenseFlowのコードを読みながら説明できるようにするための案内です。暗記するより、画面操作を1回行い、該当ファイルとテストを開いて確認してください。

## まず押さえる構成

ExpenseFlowはSpring MVCとThymeleafでHTMLをサーバーから返し、Spring Data JPAでPostgreSQLへアクセスする単一アプリです。

- `src/main/java/jp/example/expenseflow/feature/expense/web/ExpenseController.java`
  - URLを受け取り、Form DTOを受け取り、Serviceの結果を画面または303へ渡す。
- `src/main/java/jp/example/expenseflow/feature/expense/service/ExpenseService.java`
  - 認証ユーザーの特定、権限、入力、状態遷移、トランザクションを担当する。
- `src/main/java/jp/example/expenseflow/feature/expense/repository/ExpenseRequestRepository.java`
  - 本人一覧と承認待ち一覧のSQL/JPAクエリを担当する。
- `src/main/java/jp/example/expenseflow/feature/expense/domain/ExpenseRequest.java`
  - 申請の値と、編集・申請・承認・差戻しの状態遷移を担当する。
- `src/main/java/jp/example/expenseflow/feature/expense/domain/ExpenseEvent.java`
  - CREATE、UPDATE、SUBMIT、APPROVE、RETURNの履歴を表す。
- `src/main/resources/templates/expenses/` と `templates/approvals/`
  - Thymeleafの日本語画面。`th:text`は表示時にHTMLエスケープする。

## 具体例：社員が下書きを作成する流れ

1. ブラウザが `GET /expenses/new` を呼ぶと、`ExpenseController.newExpense` が空の `ExpenseForm` を画面へ渡す。
2. HTMLフォームのPOSTには、件名・用途・分類・利用日・金額とCSRFトークンが含まれる。`ExpenseController.create` は `ExpenseForm` だけをバインドする。
3. `ExpenseService.create` はログイン中のusernameを `CurrentUserService` へ渡し、DBの `AppUser` と部署を取得する。申請者や部署をフォームの値から決めない。
4. Serviceは金額の元文字列をtrimし、`[0-9]+`だけを許可してから `BigDecimal` に変換する。日付は注入された `Clock` のAsia/Tokyoでの今日以前か確認する。
5. `ExpenseRequest.create` がDRAFTと作成日時を設定し、`ExpenseRequestRepository.saveAndFlush` が `expense_requests` へ保存する。
6. 同じServiceのトランザクション内で `ExpenseEvent.record(... CREATE ...)` を保存する。成功するとControllerは303で `/expenses/{id}` へリダイレクトする。

「Controller→Service→Repository→DB」の分担を確認するテストは、`src/test/java/jp/example/expenseflow/feature/expense/ExpenseIntegrationTest.java` の作成・編集・申請テストです。

## DTOとEntity

`ExpenseForm`、`ApprovalForm` はHTTP入力用のDTOです。ユーザーが送信した文字列を一時的に保持し、入力エラー画面へ元の値を戻す役割があります。Entityである `ExpenseRequest` にフォームを直接バインドしないため、`applicant`、`department`、`status`、日時などをHTTPリクエストで差し替えられません。

Entityの状態変更は一般的なsetterではなく、`updateDetails`、`submit`、`approve`、`returnForRevision` という業務メソッドに限定しています。例えばAPPROVEDを再度承認しようとすると、Serviceの409判定とドメインの許可状態チェックで保存されません。

## 認証・認可・CSRF

`src/main/java/jp/example/expenseflow/feature/auth/config/SecurityConfig.java` でフォームログイン、保護URL、CSRF、POSTログアウトを設定しています。ログインはSpring Securityが処理するため302、経費の成功POSTはControllerのPRGで303です。

認証は「誰か」を確認し、認可は「その申請を操作してよいか」を確認します。`ExpenseService.requireOwned` は申請者本人だけの変更操作を許可します。`requireApprover` によるロール確認後、`requireApprovalTarget` は同部署・本人以外・DRAFT以外という対象へのアクセスを検証し、`approve`／`returnToApplicant` がSUBMITTEDからだけ遷移できることを検証します。画面からボタンを隠すだけではなく、URLを直接呼んでもServiceで拒否します。存在を知らせない対象は404、社員の承認操作は403、見えるが状態が違う操作は409です。

CSRFは、別サイトからログイン済みブラウザを使ってPOSTされることを防ぐ仕組みです。フォームのhidden `_csrf` を削除したテストは403になります。これは申請の所有者チェックとは別の防御です。

## `@Transactional` と `@Version`

`ExpenseService.update` や `approve` では、申請の変更と履歴のINSERTを同じ `@Transactional` の中で行います。履歴保存に失敗したら、申請の状態・versionもロールバックされる必要があります。テストではPostgreSQLトリガーで履歴INSERTを失敗させています。

`ExpenseRequest.version` はJPAの `@Version` です。画面のversionをServiceで現在値と先に比較し、古い画面を409にします。それでも別トランザクションが同じversionを読み、flush/commitで競合した場合は、DBのUPDATE条件にversionが含まれ、JPAが楽観ロック例外を発生させます。`ApprovalIntegrationTest` の同時承認テストは、2トランザクションがversion 0を読み終わるまで同期してからflushするため、この違いを確認できます。

## 検索がDBで行われる理由

本人一覧は `ExpenseRequestRepository.findOwnPage` または `findOwnPageWithoutQuery` で、申請者ID、状態、分類、利用日、件名をSQL/JPA側で絞り込みます。Serviceはページサイズ20と `updatedAt DESC, id DESC` を指定します。承認待ちは部署ID、本人以外、SUBMITTEDに固定し、`submittedAt ASC, id ASC` で取得します。

件名検索の `%` と `_` はワイルドカードにせず、Serviceの `escapeLike` でエスケープし、クエリの `escape '\\'` と組み合わせています。検索条件の入力検証、0件、21件目、リテラル記号は `ExpenseIntegrationTest` で確認します。

## 申請の状態を読む

通常の流れは次のとおりです。

- DRAFT：本人が編集・削除・申請できる。
- SUBMITTED：同部署の別APPROVERが承認または差戻しできる。本人は編集できない。
- RETURNED：本人が修正して再申請できる。差戻し理由は履歴に残る。
- APPROVED：終端。編集や再承認はできない。

状態変更の入口は `ExpenseService` と `ExpenseRequest` の業務メソッドです。fixtureを用意するテストではDBを直接準備していますが、実アプリケーションに状態変更の裏口はありません。

## テストの目的

`SecurityIntegrationTest` はログイン、未認証アクセス、CSRF、ログアウトを確認します。`ExpenseIntegrationTest` は申請の正常系、入力境界、認可、検索、version、HTMLエスケープ、履歴ロールバックを確認します。`ApprovalIntegrationTest` は承認待ち、承認・差戻し、自己処理や部署境界、競合を確認します。`DemoDataInitializerIntegrationTest` はdemoデータ45件とイベントが、再実行で増えず既存値を上書きしないことを確認します。

テストはRepositoryのモックだけではなく、TestcontainersでPostgreSQL 17を起動し、Flywayを適用した実DBへMockMvcからHTTPを送ります。これにより、JPAのクエリ、DB制約、version、トランザクションを一緒に確認できます。

## AI支援と自分で確認する範囲

この開発では、設計・レビュー、コード実装、テスト作成・実行、資料作成にAI支援を利用しました。本人の理解度や未実施の検証をAIの作業結果から推定せず、実際に説明する前に次の点を自分でファイルとテスト結果に照らして確認してください。

- なぜForm DTOからEntityへ直接バインドしないのか。
- `requireOwned` と `requireApprovalTarget` の判定順、403/404/409の違い。
- `@Transactional` が申請と履歴を一緒に戻す理由。
- Serviceのversion比較とJPA `@Version` の競合検知の違い。
- `ExpenseRequestRepository` の条件、並び順、ページングがSQL側で行われる理由。
- CSRFトークンがログイン・ログアウト・各POSTフォームに必要な理由。

実行できなかったローカル検証を、実行済みとは説明しません。Java 21・Dockerを使ったGitHub ActionsのURL、テスト件数、未実行理由は `docs/test-results.md` に記録しています。
