# CLAUDE.md — 自律開発ガイド（最重要）

このファイルは、Claude（および任意の自律エージェント）が **外部を参照せずに**
この Forge Mod を拡張・新規開発するための運用書である。作業前に必ず読むこと。

---

## 0. このプロジェクトの正体

- **Minecraft 1.20.1** 向け **Minecraft Forge** Mod
- mod id = `examplemod`（変更時の手順は §6）
- 言語: Java 17 / ビルド: Gradle (ForgeGradle 6.0)
- 例として「アイテム1つ・ブロック1つ・クリエイティブタブ1つ」が登録済み
- 詳細トピックは `docs/01`〜`docs/09` に分割。迷ったら該当 docs を開く

## 1. 絶対に守るルール（違反するとビルド/起動が壊れる）

1. **Java は 17 固定**。`build.gradle` の `toolchain.languageVersion = JavaLanguageVersion.of(17)` を変更しない。実行 JDK が 21 でも toolchain が 17 を強制する（JDK17 が見つかる必要あり）
2. **mod id は完全一致**。`@Mod` 引数（`ExampleMod.MOD_ID`）／`mods.toml` の `modId`／`assets/<id>/`／`data/<id>/` のフォルダ名はすべて同じ文字列。小文字英数字と `_` のみ
3. **`pack.mcmeta` の `pack_format` は 15**（1.20.1 固定）。下げない
4. **登録は必ず `DeferredRegister` 経由**。`new` した Block/Item を直接フィールド公開して使い回さない。`RegistryObject<T>` を使い、値が必要なときに `.get()`
5. バージョン定数は `gradle.properties` が単一の真実。Java コードや `mods.toml` に直書きしない（`mods.toml` は `${...}` トークン、build.gradle が置換）

## 2. よく使うコマンド

| 目的 | コマンド |
|---|---|
| ビルド（jar 生成） | `./gradlew build` → `build/libs/examplemod-1.0.0.jar` |
| クライアント起動（手動確認） | `./gradlew runClient` |
| サーバ起動 | `./gradlew runServer` |
| データ生成（任意） | `./gradlew runData` |
| 依存の再取得 | `./gradlew build --refresh-dependencies` |
| タスク一覧 | `./gradlew tasks` |

`./gradlew --version` は Gradle **8.1.1** を表示すれば正常。

## 3. ネットワーク前提（重要）

ForgeGradle は初回ビルドで `maven.minecraftforge.net` と `libraries.minecraft.net`
から Forge / Minecraft を取得する。**これらがネットワークポリシーで遮断されている環境では
`./gradlew build` / `runClient` は完了しない**（`./gradlew --version` と JSON 検証は可能）。

- 遮断環境では「コード/JSON/構成の静的な正しさ」までを保証し、ビルド不可を**正直に報告**する
- 開放環境（または依存キャッシュ済み）では通常どおりビルド・起動できる
- 切り分け: `curl -s -o /dev/null -w "%{http_code}" https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml`（403/000 なら遮断）

## 4. 変更後に必ず行う検証（順番厳守）

1. JSON 全件パース:
   `for f in $(find src -name '*.json'); do python3 -m json.tool "$f" >/dev/null || echo "BAD $f"; done`
2. mod id 一貫性:
   `grep -rn examplemod src/main/resources/META-INF/mods.toml; ls src/main/resources/assets src/main/resources/data`
3. ネットワーク開放時のみ: `./gradlew build`（`BUILD SUCCESSFUL` を確認）
4. UI を伴う機能は `./gradlew runClient` で実機確認（headless 環境では不可 → その旨明記）
5. 報告は「実際に検証できた範囲」と「できなかった範囲」を分けて書く。憶測で "動作確認済" と書かない

## 5. 機能を追加するときの基本動作

- アイテム追加 → `docs/04-アイテム追加.md` の手順。コピー元は `src/.../registry/ModItems.java`
- ブロック追加 → `docs/05-ブロック追加.md`。コピー元は `src/.../registry/ModBlocks.java`
- 新カテゴリ（エンチャント/エンティティ等）→ `docs/03-Modの基本.md` の DeferredRegister 節
- 汎用レシピ（何を足す場合でも）→ `docs/09-機能追加ワークフロー.md`
- バグ/エラーが出た → `docs/07`（症状→原因 早見表）→ 解決しなければ `docs/10-バグとエラー対応.md`（切り分け・調査・修正・報告の手順書）

新規登録物には必ずセットで用意する: **登録コード / モデル JSON / lang（en_us と ja_jp）/ テクスチャ PNG**。
ブロックはさらに **blockstate / loot_table**。1つでも欠けると紫黒テクスチャや無音ドロップになる（`docs/07`）。

## 6. mod id / package を変えるとき

`examplemod` を別名にする場合、以下をすべて揃える（漏れ厳禁）:
- `gradle.properties` の `mod_id` と `mod_group_id`
- `@Mod` 定数 `ExampleMod.MOD_ID` と Java package（`src/main/java/...`）
- `assets/<id>/` と `data/<id>/` のフォルダ名
- lang キー（`item.<id>.*`, `block.<id>.*`, `itemGroup.<id>.*`）とモデル/テクスチャの名前空間 `<id>:...`

## 7. Git 運用

- 開発・push 先ブランチ: **`claude/minecraft-mod-docs-Wkrcu`**（指示なく他ブランチへ push しない）
- push: `git push -u origin claude/minecraft-mod-docs-Wkrcu`（ネットワーク失敗時のみ指数バックオフで最大4回）
- PR はユーザーが明示要求したときだけ作成する
- `build/` `run/` `.gradle/` 等はコミットしない（`.gitignore` 済み）

## 8. やってはいけないこと

- Java toolchain を 17 以外に変える / `pack_format` を下げる
- `DeferredRegister` を使わず静的初期化でレジストリへ直接登録する
- 検証していないのに「動作確認済み」と報告する
- 大規模リファクタを伴う変更を勝手に進める（曖昧なら確認する）
