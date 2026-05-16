# examplemod — Minecraft Forge 1.20.1 Mod スキャフォールド

Minecraft **1.20.1** / **Forge** 向けの最小構成 Mod。
「自律的に Mod を作り続ける」ことを目的に、要点をまとめた日本語ドキュメントを同梱している。

## これは何か

- すぐビルドできる Forge MDK 互換プロジェクト
- 例として **アイテム1つ / ブロック1つ / クリエイティブタブ1つ** が登録済み
- 各 `registry/*.java` と各 JSON は、新要素を足すときの**コピー元テンプレート**

## クイックスタート

前提: **JDK 17**（Forge 1.20.1 必須）。リポジトリ直下で:

```bash
./gradlew --version     # Gradle 8.1.1 が表示されれば OK
./gradlew build         # build/libs/examplemod-1.0.0.jar を生成
./gradlew runClient     # Minecraft を起動して手動確認
```

> 初回 `build` は `maven.minecraftforge.net` 等から Forge/Minecraft を取得する。
> ネットワークが遮断された環境ではビルドは完了しない（詳細: `docs/07` と `CLAUDE.md` §3）。

## ドキュメント目次

| ファイル | 内容 |
|---|---|
| `CLAUDE.md` | **自律開発ガイド（最初に読む）**。ルール・コマンド・検証手順 |
| `docs/01-環境構築.md` | JDK17 / Gradle / 主要タスク / Java21 環境での注意 |
| `docs/02-プロジェクト構成.md` | ディレクトリと主要ファイルの役割 |
| `docs/03-Modの基本.md` | `@Mod`・イベントバス・ライフサイクル・DeferredRegister |
| `docs/04-アイテム追加.md` | アイテム追加の完全手順 + チェックリスト |
| `docs/05-ブロック追加.md` | ブロック追加の完全手順 + チェックリスト |
| `docs/06-アセットとデータ.md` | resources のパス規約 / data generation |
| `docs/07-よくあるエラーと対処.md` | 典型エラーと解決（症状→原因 早見表） |
| `docs/08-ビルドとリリース.md` | ビルド成果物・バージョニング・配布 |
| `docs/09-機能追加ワークフロー.md` | 何を足すときも使える汎用レシピ |
| `docs/10-バグとエラー対応.md` | バグ/エラーの切り分け・調査・修正・報告の手順書 |
| `docs/epicfight/11-EpicFight戦闘スタイル.md` | Epic Fight の戦闘スタイルを武器に割り当てる（datapack 方式） |
| `docs/epicfight/12-EpicFight特異点ボス.md` | 段階的に強くなるボス「特異点」+ Epic Fight/WoM のプレイヤー行動転用（API 方式） |

## ライセンス

`gradle.properties` の `mod_license` を実際のライセンスに更新すること（既定: All Rights Reserved）。
