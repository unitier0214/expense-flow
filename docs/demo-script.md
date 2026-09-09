# Demo Script（フェーズ4完了時点）

この手順はdemoプロファイル専用の架空fixtureを使う。認証情報は公開用の検証値であり、本番環境では使用しない。

## 起動

1. `.env.example`を`.env`へコピーし、`POSTGRES_PASSWORD`を設定する。
2. `SPRING_PROFILES_ACTIVE=demo docker compose up --build`を実行する。
3. `/login`を開く。demo初回起動では営業部・開発部、5ユーザー、状態を分散した申請45件と履歴が冪等投入される。

## 3分デモ

1. `demo.employee` / `demo-password`でログインし、`/expenses`で本人の申請だけが表示されることを確認する。ヘッダーのユーザー表示とPOSTログアウトも示す。
2. 「新規申請」から件名、用途、分類、利用日、整数金額を入力して保存する。詳細でDRAFT、金額、CREATE履歴を確認する。
3. 編集画面で金額に`1.5`を入力して送信し、項目別400エラーと入力値保持を確認する。フォームの送信先とキャンセル先が同じ申請IDの編集画面を指すことを示す。その後、整数へ直して保存し、UPDATE履歴とversion増加を確認する。
4. 詳細画面の「申請する」を押し、状態がSUBMITTED（申請中）になり、直近申請日時とSUBMIT履歴が表示されることを確認する。
5. ログアウトし、`demo.approver.sales.1` / `demo-password`でログインする。「承認待ち」で本人以外・同部署・申請中だけが表示されることを示す。対象を開くと詳細・履歴と承認／差戻しフォームが表示される。
6. 差戻し理由を入力して差し戻す。`demo.employee`で再ログインし、理由が履歴に残ったRETURNED申請を確認する。編集して再申請すると、直近申請日時が更新され、SUBMIT履歴が増える。
7. `demo.approver.sales.2` / `demo-password`でログインし、承認待ちから対象を開いて任意コメント付きで承認する。詳細の状態がAPPROVED（承認済み）になり、承認コメントと履歴が表示されることを確認する。
8. POSTログアウト後に`/expenses`へアクセスし、ログイン画面へ302リダイレクトされることを示す。Composeを停止・再起動（ボリューム削除なし）して、DBの申請とデモデータが残ることを確認する。

## デモ用ユーザー

- 社員：`demo.employee` / `demo-password`
- 営業部承認者1：`demo.approver.sales.1` / `demo-password`
- 営業部承認者2：`demo.approver.sales.2` / `demo-password`
- 開発部社員：`demo.employee.dev` / `demo-password`
- 開発部承認者：`demo.approver.dev` / `demo-password`

CSV出力はフェーズ5で追加する。Phase4のCompose smokeは上記の社員→差戻し→再申請→別承認者承認フロー、対象申請の状態・履歴、CSRF・version、ログアウト後の保護URL拒否、DB永続化をHTTPで確認済みである。curlによるHTTP確認を実ブラウザ操作とは区別する。
