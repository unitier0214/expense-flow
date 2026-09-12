# デモ画面の取得元

- ソースSHA：`b9ca840a6d29692aa4bca281c9ed1f0d527f1ace`。
- 撮影CI：[run 34681842105](https://github.com/unitier0214/expense-flow/actions/runs/34681842105)。このSHAに対してsuccessを確認。
- 手段：CIのUbuntu runnerで実際のSpring Boot＋PostgreSQLを起動し、`scripts/browser-smoke.js`のChromium／Playwright 1.55.0で操作・撮影。
- 画像は生成モックではなく、撮影PNGをそのまま保存。元情報は[source.json](source.json)。
- 撮影画像9枚のうち以下の5枚をリポジトリへ保存し、Astraが目視確認した。その他4枚はCI artifactにあり、今回の目視対象には含めていない。

| 掲載ファイル | CI内の元ファイル | 画面 |
|---|---|---|
| expenses-list.png | 02-expenses-list.png | 本人の一覧・検索・ページ移動 |
| edit-validation-error.png | 04-edit-validation-error.png | 小数金額の400エラーと入力保持 |
| returned-detail.png | 06-returned-detail.png | 差戻しの状態・理由・履歴 |
| approved-history.png | 07-approved-detail.png | 別承認者による承認と7件の履歴 |
| mobile-list.png | 09-mobile-list.png | 375×812の一覧（表は横スクロール） |

日本語の文字表示、項目エラーの表示位置、差戻し／承認済みの状態と履歴、代表レイアウトを確認。実機タッチ操作やブラウザ互換性を確認したという意味ではない。
