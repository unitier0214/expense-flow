# 最終総合レビュー修正（2026-09-12）

開始時main：`42d7e31478c0310110ca8e1b7a56255be3752d8c`。ユーザーの追加指示によりAstraが実装を担当。

| 指摘 | 対応 |
|---|---|
| R1 | Composeのアプリをlocalhost限定に変更。標準DBは非公開 |
| R2 | 巨大pageでJPAのoffset上限を超えない取得・件数保持・ページ番号のlong表示・次ページ判定、両一覧の回帰テスト |
| R3 | MVCの4xx・Allowヘッダーを維持。想定外の500と照合IDは継続 |
| R4 | RETURN履歴保存失敗、文字数超過、JST境界の実DBテスト。T01〜T13のテスト対応を明示 |
| R5 | demo起動を一本化。任意のMaven用DB公開設定・8081起動とCIでの起動検証 |
| R6 | 学習ガイド・CSVデモ・AI利用表記を訂正。実画面4枚をREADMEに掲載し、375px幅1枚と取得元記録を保存 |

## 検証

実装・画面撮影の検証対象：[`b9ca840a6d29692aa4bca281c9ed1f0d527f1ace`](https://github.com/unitier0214/expense-flow/commit/b9ca840a6d29692aa4bca281c9ed1f0d527f1ace)。対応する[CI run 34681842105](https://github.com/unitier0214/expense-flow/actions/runs/34681842105)はsuccess。ジョブ一覧とtest／dependency-scanの実ログを確認した。画像掲載・結果記録後のコミットのCIは [PR #1](https://github.com/unitier0214/expense-flow/pull/1) にも対応を記録する。

| 検証 | 実測結果 |
|---|---|
| `./mvnw --batch-mode test` | 57件、失敗0、エラー0、スキップ0、BUILD SUCCESS |
| `./mvnw --batch-mode verify` | 57件、失敗0、エラー0、スキップ0、BUILD SUCCESS |
| 内訳 | Expense 24、Approval 12、CSV 7、Security 12、DemoDataInitializer 1、DemoSeedMigration 1 |
| Compose設定 | appのhost_ipが127.0.0.1、標準dbにportsがないことをJSONで検証。Maven用overrideの構文検証も成功 |
| Compose HTTP | ログイン302→本人一覧200→作成303→編集303→申請303→CSV200→差戻し303→修正・再申請303→別承認者承認303→ログアウト302→保護URL302 |
| DB永続化 | DB内の表示名更新→Compose down/up→更新値保持が成功。ボリューム削除なし |
| 実ブラウザ自動操作 | Chromium＋Playwrightで作成、編集エラーから同一IDへの復帰、申請、差戻し、再申請、別承認者承認、ログアウト後の保護URL拒否が成功 |
| 375px幅 | 一覧を375×812で表示し、ドキュメント全体の横はみ出しがないことを確認。表は横スクロール方式 |
| README Maven経路 | Ubuntu runner、Java 21、Composeのlocalhost DBポート、明示的な接続変数、8081で`spring-boot:run`を実行。health UP・ログイン画面200 |
| 依存検査 | CycloneDX SBOM 105件、生成jarのBOOT-INF/lib 92本。Trivyの実対象はtarget/bom.json／cyclonedx／Java／jar、脆弱性0件 |

[ブラウザartifact](https://github.com/unitier0214/expense-flow/actions/runs/34681842105/artifacts/10293109614)と[依存検査artifact](https://github.com/unitier0214/expense-flow/actions/runs/34681842105/artifacts/10293024550)を保存。画像のうちREADMEに掲載した4枚と375px幅1枚はAstraが目視し、日本語表示・項目エラー・状態・履歴・レイアウトを確認した。[取得元記録](images/README.md)を参照。

初回の修正コミット`8fee19c0a801367b79c63dd2c8f25b0608e8a0d3`の[CI 34681694058](https://github.com/unitier0214/expense-flow/actions/runs/34681694058)では57件中2件が失敗した。新しい境界テストにより、Spring Dataの`PageImpl.hasNext()`のint加算も最大ページ番号で桁あふれすることを確認したため、Serviceでlongを使って判定し直した。上記の成功結果はその修正後のものである。

## 確認範囲と残る事項

- 今回のJava・Docker・ブラウザ実行はGitHub Actions上。ローカルで同じJava／Dockerテストを再実行したとは扱わない。
- 実ブラウザ自動操作、保存画像5枚の目視、curl／MockMvcによるHTTP検証を区別する。本人による手動操作、実機スマートフォン、Safari／Firefox、PowerShellでの起動は未確認。
- 外部公開URL、公開環境でのHTTPS・Cookie・リダイレクト、公開環境のDB永続化は未確認。
- 依存検査0件は実行時点の結果であり、将来の脆弱性を保証しない。応募時にはREADMEのデモを本人が操作し、認可・トランザクション・競合・CSVを自分で説明できるか確認する。

## 公開環境

現行のJava／Spring Boot、JPA、PostgreSQLをSitesのWorkers実行環境へそのまま配置することはできない。操作可能な公開デモにはJavaコンテナとPostgreSQLを稼働させる公開先が必要。[ブラウザ用の公開環境案](browser-demo-hosting.md)にRenderの設定候補を記載した。利用アカウントとプランは未指定で、外部環境へのデプロイは未実施。
