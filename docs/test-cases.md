# Test Cases

フェーズ1〜5の検証に、最終総合レビューの回帰テストを追加した。テストの所在は以下の通り。実行結果と対象SHAは [test-results.md](test-results.md) を参照する。

略記：S = `SecurityIntegrationTest`、E = `ExpenseIntegrationTest`、A = `ApprovalIntegrationTest`、C = `CsvIntegrationTest`。

| ID | 検証対象 | 主なテストメソッド／CI手順 |
|---|---|---|
| T01 | 認証・未認証・CSRF・ログアウト後再認証 | S.`validLoginReachesExpensesPage`、`unauthenticatedUserIsRedirectedToLogin`、`loginPostWithoutCsrfIsRejected`、`logoutPostWithoutCsrfIsRejected`、`loggedOutUserMustAuthenticateAgainForProtectedPage` |
| T02 | 必須・文字数・金額・JST日付境界 | E.`invalidFormReturns400AndKeepsOriginalInput`、`invalidAmountFormatsAreRejectedWithoutRounding`、`boundaryValuesAndJstTodayAreAccepted`、`overlongTitleAndPurposeAreRejectedForCreateAndEditWithoutChangingData`、`sameExpenseDateChangesFromFutureToTodayAtJstMidnight` |
| T03 | 作成・編集・申請・再申請・履歴・入力復帰 | E.`employeeCanCreateEditSubmitAndSeeHistory`、`returnedRequestCanBeEditedAndResubmittedButNotDeleted`、`editValidationKeepsPathIdAndAllowsRecoveryOnTheOriginalRequest`、`fullWidthWhitespaceIsRejectedForCreateAndEditWithoutChangingData` |
| T04 | 同部署の別承認者の承認・状態制約 | A.`approverCanApproveAndStoresOptionalComment`、`onlySubmittedCanBeApprovedOrReturned`、E.`approvedRequestCannotBeEditedDeletedOrSubmitted` |
| T05 | 差戻し理由・文字数・全角空白・再申請 | A.`returnRequiresReasonAndEmployeeCanEditAndResubmit`、`fullWidthWhitespaceReturnReasonIsRejectedWithoutChangingHistory` |
| T06 | ロール・部署・自己処理・DRAFT閲覧制約 | A.`employeeCannotOpenApprovalPageOrApprove`、`approvalTargetAccessRejectsSelfOtherDepartmentAndDraft`、E.`accessIsRestrictedByOwnerAndDepartment` |
| T07 | ID改変・サーバー管理項目の上書き拒否 | E.`unexpectedApplicantParametersCannotChangeServerOwnedFields`、`accessIsRestrictedByOwnerAndDepartment`、`editValidationKeepsPathIdAndAllowsRecoveryOnTheOriginalRequest` |
| T08 | version必須・形式・古い画面・独立トランザクション競合 | E.`missingOrMalformedVersionsAreBadRequests`、`staleVersionsAreRejectedWithoutChangingDataOrHistory`、`concurrentUpdatesAllowOneCommitAndOneConflict`、A.`approvalVersionAndCommentInputsReturnMeaningfulBadRequest`、`concurrentApprovalsAllowOneCommitAndOneOptimisticConflict`、`concurrentApprovalAndReturnAllowOneCommitAndOneOptimisticConflict` |
| T09 | UPDATE／APPROVE／RETURN履歴失敗時のロールバック | E.`eventFailureRollsBackExpenseChange`、A.`approvalEventFailureRollsBackStatusVersionAndHistory`、`returnEventFailureRollsBackStatusVersionAndHistory`。PostgreSQLトリガーを使用 |
| T10 | 本人検索・リテラルLIKE・20件ページング・巨大page・不正検索 | E.`listUsesOwnFiltersLiteralLikeCharactersAndTwentyItemPages`、`invalidSearchAndOutOfRangePageHaveDefinedResponses`、`hugePagesKeepOwnFiltersTotalsAndPositiveDisplayNumber`、A.`approvalPageShowsOnlySameDepartmentOthersInSubmittedOrder` |
| T11 | HTMLエスケープ | E.`userInputIsHtmlEscaped`。MockMvcでHTML出力を検証。ブラウザでの攻撃コード実行検証とは区別する |
| T12 | Flyway・demo冪等性・Compose・Maven起動・ブラウザ | S.`flywayCreatesTheExpectedSchema`、`DemoDataInitializerIntegrationTest.demoDataIsCreatedIdempotently`、`DemoSeedMigrationIntegrationTest.v2BackfillsDuplicateLegacyTitlesOnce`、CIのtest／verify／Compose smoke（認可・CSV・永続化）／`scripts/maven-smoke.sh`／`scripts/browser-smoke.js` |
| T13 | CSVの認可・検索・順序・上限・引用・数式対策 | C.`unauthenticatedUserCannotExport`、`exportUsesOwnSearchFiltersAndUpdatedOrder`、`exportWithNoMatchesReturnsBomAndHeaderOnly`、`exportEscapesJapaneseQuotesCommaNewlineAndFormulaPrefix`、`exportExactlyOneThousandRows`、`exportOverOneThousandRowsReturnsBadRequestInsteadOfTruncating`、`expenseListCsvLinkKeepsSearchConditions` |

MVCの404／405とAllowヘッダーはE.`mvcClientErrorsKeepStatusAndAllowHeader`で検証し、実DB障害の500・照合ID・内部情報非表示はT09の差戻しテストで検証する。

自動テストはRepositoryのモックだけで済ませず、TestcontainersのPostgreSQL 17とMockMvcを使用する。RETURNED／APPROVEDの状態fixtureはテスト側のJDBCで用意し、実アプリケーションに状態変更の裏口は追加していない。

フェーズ3の承認テストは `src/test/java/jp/example/expenseflow/feature/expense/ApprovalIntegrationTest.java` に置き、承認待ち一覧、ロール・部署・自己処理の認可、理由・version入力、状態遷移、履歴ロールバック、PostgreSQLの実楽観ロックを確認する。

フェーズ4では、`src/main/java/jp/example/expenseflow/config/DemoDataInitializer.java`が営業部23件・開発部22件の45件を状態分散して投入し、CREATE／SUBMIT／RETURN／APPROVE履歴を生成することを確認した。`DemoDataInitializerIntegrationTest`は再実行時の件数、既存申請の内容、履歴が変わらないことを検証する。Compose smokeは実HTMLからCSRF・versionを抽出し、対象申請の件名と状態ラベル、差戻し理由、承認コメント、履歴表示を確認する。

フェーズ5のCSVテストは `src/test/java/jp/example/expenseflow/feature/expense/CsvIntegrationTest.java` に置いた。CSVは本人のDB検索結果だけを出力し、status・category・利用日両端・件名検索、更新日時降順を検証する。1,000件は全行、1,001件は400と絞り込み案内を確認し、BOM、固定ヘッダー、Content-Disposition、RFC 4180相当の引用、改行・引用符・カンマ、日本語、数式セル接頭辞をバイト列・文字列で検証する。CIのCompose smokeでも実HTMLの検索条件を使ったCSV 200と対象行を確認する。

## 最終レビュー回帰

- `DemoDataInitializerIntegrationTest.demoDataIsCreatedIdempotently`は、seed markerの件数、同名申請の追加、seed申請の件名変更、DRAFT seedの削除、initializerの複数回実行後に申請・履歴が復活・増殖しないことを確認する。
- `DemoSeedMigrationIntegrationTest.v2BackfillsDuplicateLegacyTitlesOnce`は、新規PostgreSQLでV1だけを適用した既存DBに同名の旧デモ申請を用意し、V2が最小IDを一つのseedへバックフィルすることを確認する。
- `ExpenseIntegrationTest.fullWidthWhitespaceIsRejectedForCreateAndEditWithoutChangingData`は、件名・用途の全角スペースだけを項目別400とし、申請・version・履歴を変えず、Entityの直接業務メソッドも同じ判定になることを確認する。
- `ApprovalIntegrationTest.fullWidthWhitespaceReturnReasonIsRejectedWithoutChangingHistory`は、全角スペースだけの差戻し理由を項目別400とし、状態・version・履歴を変えないことを確認する。
- `.github/workflows/ci.yml`のPlaywright実ブラウザ smokeは、CI上のChromiumでログイン、作成、編集エラーからの元URL復帰、訂正、申請、差戻し、再申請、別承認者の承認、ログアウト後の保護URL拒否を操作し、画面スクリーンショットをartifactへ保存する。

