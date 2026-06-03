# CLAUDE.md — 自律開発ガイド（最重要）

このファイルは、Claude（および任意の自律エージェント）が、同梱 docs（`docs/01`〜、
`docs/epicfight/`）を **一次情報・出発点** としつつ、不足・不確かな点は **外部参照
（公式 Forge/Minecraft ドキュメント・Web 検索・Epic Fight/WoM の配布元やリポジトリ等）も
積極的に活用** して、この Forge Mod を拡張・新規開発するための運用書である。作業前に必ず読むこと。

**情報源の優先順位**（後段ほど版差・誤りに注意）:
1. **実コード／実プロジェクト**（`src/`・`build.gradle`・`gradle.properties` 等、いま在るものが真実）
2. **同梱 docs**（`docs/`・本ファイル。このプロジェクト向けに整理済み）
3. **公式・版一致の外部情報**（MinecraftForge / NeoForge ではなく Forge、Mojang・MCP/Parchment 等）
4. **コミュニティ情報**（フォーラム・Wiki・解説記事・動画）

- 外部情報は必ず **MC 1.20.1 / Forge 47 系** に一致することを確認し、**実コードと突き合わせてから**採用する
  （API は版で別物になりやすい。1.20.2+/1.19 以前の手順をそのまま持ち込まない）
- 外部参照で得た知見でも、未検証なら「動作確認済み」と書かない（§4・§8 の方針を踏襲）

---

## 0. このプロジェクトの正体

- **Minecraft 1.20.1** 向け **Minecraft Forge** Mod
- mod id = `examplemod`（変更時の手順は §6）
- 言語: Java 17 / ビルド: Gradle (ForgeGradle 6.0)
- 登録済みの実例: アイテム（`example_item` / `example_sword`）・ブロック（`example_block`）・
  クリエイティブタブ（`example_tab`）・スポーンエッグ（`singularity_spawn_egg`）
- さらに段階制ボス **Singularity（特異点）** が実装済み: エンティティ登録（`registry/ModEntities.java`）／
  本体（`entity/Singularity.java`、`skillStage` 0..3 で段階強化）／プレイヤー型レンダラ（`client/`）／
  OP コマンド `/singularity_stage {next|set <0..3>}`（`command/SingularityStageCommand.java`）。詳細は `docs/epicfight/12`
- 詳細トピックは `docs/01`〜`docs/10`、Epic Fight 関連は `docs/epicfight/` に分割。迷ったら該当 docs を開く

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

> **Windows PowerShell** では `./gradlew` の代わりに `.\gradlew.bat <task>` を使う（例: `.\gradlew.bat build`）。
> bash/Git Bash/WSL/Linux/CI では `./gradlew <task>` のまま。以降 §3〜§4 のコマンドも PowerShell 版を併記する。

`./gradlew --version`（PowerShell は `.\gradlew.bat --version`）は Gradle **8.1.1** を表示すれば正常。

## 3. ネットワーク前提（重要）

ForgeGradle は初回ビルドで `maven.minecraftforge.net` と `libraries.minecraft.net`
から Forge / Minecraft を取得する。**これらがネットワークポリシーで遮断されている環境では
`./gradlew build` / `runClient` は完了しない**（`./gradlew --version` と JSON 検証は可能）。

- **ネットワーク開放時は外部参照（公式 docs・Web 検索・依存取得）を積極活用してよい**。遮断時のみ
  同梱 docs にフォールバックし、ビルド不可を**正直に報告**する（イントロの情報源優先順位を参照）
- 遮断環境では「コード/JSON/構成の静的な正しさ」までを保証し、ビルド不可を**正直に報告**する
- 開放環境（または依存キャッシュ済み）では通常どおりビルド・起動できる
- 切り分け（bash）: `curl -s -o /dev/null -w "%{http_code}" https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml`（403/000 なら遮断）
- 切り分け（PowerShell）: `try { (Invoke-WebRequest "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml" -UseBasicParsing -Method Head -TimeoutSec 10).StatusCode } catch { $_.Exception.Message }`（200 以外/例外なら遮断）

## 4. 変更後に必ず行う検証（順番厳守）

1. JSON 全件パース:
   - bash: `for f in $(find src -name '*.json'); do python3 -m json.tool "$f" >/dev/null || echo "BAD $f"; done`
   - PowerShell: `Add-Type -AssemblyName System.Web.Extensions; $js = New-Object System.Web.Script.Serialization.JavaScriptSerializer; Get-ChildItem -Recurse src -Filter *.json | ForEach-Object { $f=$_.FullName; try { $js.DeserializeObject((Get-Content $f -Raw -Encoding UTF8)) > $null } catch { Write-Host "BAD $f" } }`
   - ⚠️ PowerShell 5.1 の `ConvertFrom-Json` は **空文字キー（blockstate の `"": {...}` で頻出）と UTF-8 日本語（lang）で正当な JSON を誤って失敗扱い**にするので使わない。`-Encoding UTF8` 明示と `JavaScriptSerializer`（または `python -m json.tool <file>`）で検証すること
2. mod id 一貫性（`mods.toml` は `${mod_id}` トークンなので literal `examplemod` は出ない。literal の真実は `gradle.properties` / `ExampleMod.java` / フォルダ名で確認する）:
   - bash: `grep -rn 'examplemod' gradle.properties src/main/java/com/example/examplemod/ExampleMod.java; ls src/main/resources/assets src/main/resources/data`
   - PowerShell: `Select-String -Path gradle.properties, src/main/java/com/example/examplemod/ExampleMod.java -Pattern examplemod; Get-ChildItem src/main/resources/assets, src/main/resources/data`
3. ネットワーク開放時のみ: `./gradlew build`（`BUILD SUCCESSFUL` を確認）
4. UI を伴う機能は `./gradlew runClient` で実機確認（headless 環境では不可 → その旨明記）
5. 報告は「実際に検証できた範囲」と「できなかった範囲」を分けて書く。憶測で "動作確認済" と書かない

## 5. 機能を追加するときの基本動作

- アイテム追加 → `docs/04-アイテム追加.md` の手順。コピー元は `src/.../registry/ModItems.java`
- ブロック追加 → `docs/05-ブロック追加.md`。コピー元は `src/.../registry/ModBlocks.java`
- 新カテゴリ（エンチャント/エンティティ等）→ `docs/03-Modの基本.md` の DeferredRegister 節（エンティティの実例コピー元は `registry/ModEntities.java` + `entity/Singularity.java`）
- 武器に Epic Fight 戦闘スタイルを付ける → `docs/epicfight/11-EpicFight戦闘スタイル.md`（datapack 方式）
- プレイヤー型強敵ボス（段階的に強くなる／Epic Fight・WoM のプレイヤー行動転用）→ `docs/epicfight/12-EpicFight特異点ボス.md`（API 方式）
- Weapons of Miracles 連携（版一致・版耐性・武器の段階解禁）→ `docs/epicfight/13-WeaponsOfMiracles連携.md`
- 汎用レシピ（何を足す場合でも）→ `docs/09-機能追加ワークフロー.md`
- バグ/エラーが出た → `docs/07`（症状→原因 早見表）→ 解決しなければ `docs/10-バグとエラー対応.md`（切り分け・調査・修正・報告の手順書）

新規登録物には必ずセットで用意する: **登録コード / モデル JSON / lang（en_us と ja_jp）/ テクスチャ PNG**。
ブロックはさらに **blockstate / loot_table**。1つでも欠けると紫黒テクスチャや無音ドロップになる（`docs/07`）。
エンティティはさらに **EntityType 登録（`ModEntities`）/ 属性登録（`EntityAttributeCreationEvent`）/ レンダラ登録（`client/`・`RegisterRenderers`）/ `textures/entity/*.png` / `entity.<id>.*` lang**（スポーンエッグを足すなら `ForgeSpawnEggItem` も）。欠けると登録失敗・白画面・紫黒スキンになる。

## 6. mod id / package を変えるとき

`examplemod` を別名にする場合、以下をすべて揃える（漏れ厳禁）:
- `gradle.properties` の `mod_id` と `mod_group_id`
- `@Mod` 定数 `ExampleMod.MOD_ID` と Java package（`src/main/java/...`）
- `assets/<id>/` と `data/<id>/` のフォルダ名
- lang キー（`item.<id>.*`, `block.<id>.*`, `entity.<id>.*`, `itemGroup.<id>.*`）とモデル/テクスチャ（`textures/item/`・`textures/block/`・`textures/entity/`）の名前空間 `<id>:...`

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
