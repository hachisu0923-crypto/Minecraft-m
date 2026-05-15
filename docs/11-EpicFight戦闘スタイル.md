# 11 - Epic Fight 戦闘スタイルの追加（datapack 方式）

本 Mod の武器に **Epic Fight の戦闘スタイル（ムーブセット）** を割り当てる手順。
**datapack 方式**を主軸にする（Java コード・コンパイル依存は不要）。
具体例として、スキャフォールドに同梱した `example_sword` を使う。

## 0. 前提と仕組み

- 対象: **Epic Fight Forge 20.x（Minecraft 1.20.1）**。バージョンで JSON 細部が変わるため
  最終的な仕様は公式 wiki（§9）で**版ごとに必ず確認**すること
- Epic Fight 本体は**別途インストール**が必要（ユーザー環境 / dev 実行に Epic Fight jar が要る）
- ただし **datapack はコンパイル依存ゼロ**。`build.gradle` を変えなくてよい
- 仕組み: Epic Fight は `data/<modid>/capabilities/weapons/<item登録名>.json` を読み、
  そのアイテムに **weapon type（＝戦闘スタイル/ムーブセット）** を付与する。
  この JSON は本 Mod の jar に同梱される「組み込み datapack」になる（手書きでよい）

## 1. 手順（example_sword を例に）

### STEP 1 — 武器アイテムを用意
`docs/04-アイテム追加.md` の手順でアイテムを登録する。武器は `SwordItem` 等を使う。
スキャフォールドのコピー元（`registry/ModItems.java`）:

```java
public static final RegistryObject<Item> EXAMPLE_SWORD =
        REGISTER.register("example_sword",
                () -> new SwordItem(Tiers.IRON, 3, -2.4F, new Item.Properties()));
```

加えて `models/item/example_sword.json`（`parent: item/handheld`）、
`textures/item/example_sword.png`、`lang`（en_us/ja_jp）も `docs/04` どおり用意済み。
**Epic Fight が無くても通常の剣として機能する**（datapack はあくまで追加情報）。

### STEP 2 — capabilities JSON を作る
パス（厳守）:

```
src/main/resources/data/<modid>/capabilities/weapons/<item登録名>.json
```

本 Mod では `data/examplemod/capabilities/weapons/example_sword.json`。
**フォルダの `<modid>` とファイル名 `<item登録名>` は、登録したアイテムと完全一致**させる
（`docs/06` の名前空間規約と同じ考え方。武器=`weapons/`、防具=`armors/`）。

同梱済みの内容:

```json
{
  "type": "epicfight:sword",
  "attributes": {
    "common": {
      "armor_negation": 0.0,
      "impact": 1.1,
      "max_strikes": 1
    }
  }
}
```

### STEP 3 — `type` に戦闘スタイルを指定
`type` が**割り当てる戦闘スタイル（ムーブセット）**。`epicfight:<style>` 形式。
組込スタイルは §3 の一覧から選ぶ。例: 大剣にしたいなら `"type": "epicfight:greatsword"`。

### STEP 4 — `attributes` で挙動を調整
`attributes` はホールド状況別の数値。最小構成は `common` のみでよい。

| キー | 意味 |
|---|---|
| `common` | 片手/両手で挙動を変えない（最も簡単） |
| `one_hand` / `two_hand` | オフハンドの状態で挙動を変える（例: 槍は両手、盾持ちで片手） |
| `armor_negation` | 防御無視率（%相当。`0.0`〜） |
| `impact` | ヒット時のスタン/ノックバックの強さ |
| `max_strikes` | 1スイングで当たる最大敵数 |

`one_hand`/`two_hand` を使う例（`type` は槍など両手系を想定）:

```json
{
  "type": "epicfight:spear",
  "attributes": {
    "two_hand": { "armor_negation": 0.0, "impact": 1.5, "max_strikes": 1 },
    "one_hand": { "armor_negation": 0.0, "impact": 1.0, "max_strikes": 1 }
  }
}
```

## 2. 任意: `collider`（当たり判定）
攻撃判定ボックス。指定しなければ `type` 既定のコライダーが使われる（通常は省略でよい）。
カスタムする場合の概念（**正確な構文は版差があるため §9 の wiki 必読**）:

- `count`: 1tick 間に生成する判定数（速い斬撃で隙間を作らない）
- `size`: 判定ボックスの寸法
- `center`: 武器基準のアンカー位置

まずは省略 → 必要になってから wiki を見て足す、で十分。

## 3. 組込戦闘スタイル一覧（版依存）

確認済みの代表的な `type` 値（Epic Fight 20.x / 1.20.1）:

| 近接 | `epicfight:sword` `epicfight:longsword` `epicfight:katana` `epicfight:tachi` `epicfight:greatsword` `epicfight:spear` `epicfight:dagger` `epicfight:dual_sword` `epicfight:dual_dagger` `epicfight:fist` |
|---|---|
| 道具系 | `epicfight:axe` `epicfight:pickaxe` `epicfight:shovel` `epicfight:hoe` |
| 遠隔/その他 | `epicfight:bow` `epicfight:crossbow` `epicfight:trident` `epicfight:shield` |

**この一覧は版で増減する。**正典は公式 wiki の Weapon Type Editor / Item Capability（§9）。

### 他 Mod の武器タイプを流用する
別アドオン Mod が定義した weapon type も使える。**`epicfight:` ではなくその Mod の id** を付ける:

```json
{ "type": "coolmodid:rapier" }
```

その Mod が読み込まれていない環境では無効になる点に注意（soft-dependency 化推奨 → §5）。

## 4. 検証

datapack JSON は既存の検証パイプラインでそのままチェックできる:

```bash
# JSON 全件パース（capabilities を含む）
for f in $(find src -name '*.json'); do
  python3 -m json.tool "$f" >/dev/null 2>&1 || echo "BAD JSON: $f"
done

# 配置の一貫性（modid と登録名）
ls src/main/resources/data/examplemod/capabilities/weapons/
grep -n 'example_sword' src/main/java/com/example/examplemod/registry/ModItems.java
```

実機確認（Epic Fight 導入環境のみ）:
- 剣を持って Epic Fight の戦闘モードに入り、コンボ/モーションが `type` のものになるか
- `armor_negation` / `impact` / `max_strikes` の体感が JSON と一致するか

> **この実行環境では実機確認は不可**（Epic Fight/Cursemaven/Forge maven はネットワーク
> ポリシーで遮断・headless）。静的検証（JSON/配置）までを保証し、実機は別環境で行う
> （`docs/07` の切り分け方針と同じ）。

## 5. 任意: soft-dependency 化（実ファイルは変更しない・手順のみ）

datapack 自体は依存不要だが、Epic Fight より後にロードさせたい/未導入時に安全にしたい場合、
`src/main/resources/META-INF/mods.toml` に**任意で**次を追記できる（必須ではない）:

```toml
[[dependencies.examplemod]]
    modId="epicfight"
    mandatory=false        # 未導入でも本 Mod は起動する
    versionRange="[20,)"
    ordering="AFTER"       # Epic Fight の後に読む
    side="BOTH"
```

`mandatory=false` がポイント。`true` にすると Epic Fight 必須 Mod になる。

## 6. 任意: dev 実行で試す（Cursemaven・手順のみ）

`./gradlew runClient` で Epic Fight を一緒に動かしたいときの**任意手順**（実ファイルは変更しない）:

1. `build.gradle` の `repositories { }` に Cursemaven を追加
   `maven { url = 'https://cursemaven.com' }`
2. `dependencies { }` に Epic Fight を `runtimeOnly`（datapack 方式なので compile 不要）
   `runtimeOnly fg.deobf("curse.maven:epic-fight-<projectId>:<fileId>")`
   （`<fileId>` は CurseForge/Modrinth のファイル ID）
3. `./gradlew runClient`

> Cursemaven / Forge maven がネットワーク遮断された環境では取得できない。
> その場合は「JSON は静的検証済み・実機は別環境」と正直に報告する。

## 7. チェックリスト（武器1つにつき）

- [ ] アイテムを登録した（`docs/04`。武器なら `SwordItem` 等）
- [ ] モデル(`item/handheld`)・テクスチャ・lang(en_us & ja_jp) を用意した
- [ ] `data/<modid>/capabilities/weapons/<登録名>.json` を作った
- [ ] `<modid>` と `<登録名>` がアイテムと完全一致
- [ ] `type` が有効な戦闘スタイル（§3 / wiki）
- [ ] `attributes` が `common` か `one_hand`+`two_hand` で矛盾なし
- [ ] JSON 全件パス（§4）
- [ ] （Epic Fight 環境）実機でムーブセットが変わることを確認 or 未検証と明記

## 8. よくある失敗

| 症状 | 原因 |
|---|---|
| 戦闘スタイルが付かない（バニラ挙動のまま） | JSON のパス/ファイル名がアイテム登録名と不一致 / `weapons` 綴り違い |
| Epic Fight 起動ログに parse エラー | 不明キー・末尾カンマ・`type` の綴り違い（§4 で検出） |
| 未導入環境でクラッシュ | `mandatory=true` にした → `false` にする（§5） |
| 他 Mod タイプ `modid:x` が無効 | その Mod 未導入 / weapon type 名違い |
| dev で Epic Fight が出ない | §6 の `runtimeOnly` 未設定 or ネットワーク遮断 |

## 9. 公式リファレンス（版ごとに必ず確認）

- Epic Fight Wiki: `https://epicfight-docs.readthedocs.io/`
  - Item Capability（武器 capabilities datapack の正典）
  - Weapon Type Editor（戦闘スタイル一覧・カスタム weapon type）
  - API（独自スキル/ムーブセット/アニメーションを自作する上級者向け。Forge 1.20.1 は
    `EntityPatchRegistryEvent` を **MOD バス**・`@Mod.EventBusSubscriber(bus = MOD)` で登録）

本書は datapack 方式の確証済み最小構成を示す。`collider` 詳細や追加属性、
組込スタイルの増減は **Epic Fight の版**で変わるため、上記 wiki を最終的な根拠とすること。

> 敵にプレイヤーの戦闘行動・武器ムーブセットを転用し「段階的に強くなるボス」を
> 作る上級編は `docs/12-EpicFight特異点ボス.md`（API 方式）を参照。
