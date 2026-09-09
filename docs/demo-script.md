# Demo Script（フェーズ1）

1. `SPRING_PROFILES_ACTIVE=demo docker compose up --build` でDB readiness後にアプリを起動する。
2. `/login` を開き、`demo.employee` / `demo-password` でログインする。
3. ログイン後の基盤案内画面と、ログアウトがPOSTで行われることを確認する。
4. 開発者ツールなどからログインPOSTのCSRFトークンを外して送信し、403になることをテストで示す。

申請作成から承認までの3分デモは、申請・承認画面を実装するフェーズ2以降で完成させます。未実装機能を実装済みとして説明しません。

