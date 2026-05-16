# 13 - Weapons of Miracles 連携（Epic Fight アドオン）

Weapons of Miracles（以下 **WoM**）を本 Mod／「特異点」ボスと連携させるための専用書。
WoM 固有の事項（**版一致**・**版耐性のある参照**・武器カタログ・ライセンス）をここに集約する。
Epic Fight 全体の仕組みは `docs/epicfight/11`、ボス本体と連携設計は
`docs/epicfight/12` を参照（本書はそれらの WoM 部分の正典）。

---

## 0. これは何か / 正直な前提

- **WoM は Epic Fight の“ハード依存アドオン”**（mc1.20.1-forge）。配布ページ記載で
  武器・スキル・防具・エンチャント・エンティティを追加（点数は**版で増減**しうる。
  代表武器: Antitheus / Satsujin / Solar など）。
- WoM は **Epic Fight 本体が無いと動かない**。本 Mod は WoM/Epic Fight の
  **どちらにもハード依存しない**（`mandatory=false`＋`ModList` ガード＋フォールバック）。
- **他者の Mod を改変しない／アセットをコピーしない**（ライセンス遵守）。連携は
  **レジストリ ID 参照のみ**。連携コードは `src/` に置かず**本書のコード片**に留める
  （依存不在でビルドを壊さないため。`docs/epicfight/11`/`12` と同方針）。
- **本実行環境ではビルド/実機/ムーブセット/スキルは検証不可**（Cursemaven/Forge
  maven 遮断・headless）。後述の `unzip` による版確認は**オフラインで実施可能**＝
  最も確実な互換手段。実機確認は別環境で行い、未検証は正直に明記する（`docs/10`）。

---

## 1. 互換性（最重要・実導入前に必ず実施）

> **前提（正直に）**: Epic Fight と WoM は他者の Mod であり、両者“間”の互換性
> そのものは本 Mod から改変できない。我々が高められるのは
> **(1) 一致ペアを確実に選ぶ手順** と **(2) 連携コードの版耐性** の二つ。

### 1.1 一致ペアの決定的な選定（オフライン・唯一の本質的対策）

WoM 各ビルドが要求する Epic Fight バージョンは **WoM jar の `mods.toml`** に
宣言されている。ゲーム起動前に読める:

```bash
# WoM が要求する Epic Fight の versionRange
unzip -p WeaponsOfMiracles-*.jar META-INF/mods.toml \
  | grep -A4 'modId[ ]*=[ ]*"epicfight"'

# Epic Fight が要求する Forge 下限（本構成 forge 47.4.10 が範囲内か）
unzip -p epicfight-forge-*.jar META-INF/mods.toml \
  | grep -A3 'modId[ ]*=[ ]*"forge"'
```

→ 表示された Epic Fight `versionRange` に**収まる Epic Fight ビルド**を入れる。
憶測でなく **WoM 自身の宣言**に従うのが唯一確実。バラバラに最新を入れると
`AnimationRegistryEvent` 不在などで**ロード不可/クラッシュ**する（既知事例多数）。

### 1.2 互換性マトリクス雛形（選定結果を必ず記録）

| 役割 | 採用ビルド | 要求（jar の mods.toml より） | 確認 |
|---|---|---|---|
| Minecraft | 1.20.1 | — | ✅ |
| Forge | 47.4.10 | Epic Fight 要求 ≥ ____ | ☐ |
| Epic Fight | epicfight-forge-__.__.__-1.20.1 | WoM 要求 versionRange ____ に収まる | ☐ |
| Weapons of Miracles | WeaponsOfMiracles-__.__ | 上記 Epic Fight に一致 | ☐ |
| Java | 17（toolchain 固定・`CLAUDE.md` §1） | 共通 | ✅ |

> Epic Fight 1.20.1 の Forge 下限は版で上がる（直近は概ね **≥ 47.4.4**）。
> 本プロジェクトの `forge_version=47.4.10` は充足。**下げない**こと（`docs/01`）。

---

## 2. 任意ソフト依存の足場（手順のみ・`src/` は変更しない）

`build.gradle`（Cursemaven は `docs/epicfight/11 §6` と同じ）:

```gradle
repositories { maven { url = 'https://cursemaven.com' } }
dependencies {
    compileOnly fg.deobf("curse.maven:epic-fight-<projectId>:<fileId>")
    compileOnly fg.deobf("curse.maven:weapons-of-miracles-<projectId>:<fileId>")
}
```

`mods.toml`（`mandatory=false`＝未導入でも本 Mod は起動。`docs/epicfight/11 §5` 同形）:

```toml
[[dependencies.examplemod]]
    modId="epicfight"
    mandatory=false
    versionRange="[20,)"
    ordering="AFTER"
    side="BOTH"
[[dependencies.examplemod]]
    modId="weaponsofmiracles"
    mandatory=false
    versionRange="[1,)"
    ordering="AFTER"
    side="BOTH"
```

---

## 3. 版耐性のあるアイテム解決

WoM のアイテム登録名は**版で変わりうる**。単一 ID 直参照は脆いので、
**複数候補 ID ＋ Epic Fight 武器 capability の存在確認 ＋ 例外境界**で解決する。

```java
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** WoM 武器を「論理名→複数候補ID」で安全に解決（不一致は fallback へ降格）。 */
public static Item womWeaponOr(List<String> candidateIds, Item fallback) {
    if (!ModList.get().isLoaded("weaponsofmiracles")) return fallback;
    for (String id : candidateIds) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("weaponsofmiracles", id));
        if (it != null && hasEpicFightWeaponCap(new ItemStack(it))) return it;
    }
    return fallback;
}

/** EF 武器 capability 判定。<b>例外境界</b>が肝（API 変更でも連携だけ無効化）。 */
private static boolean hasEpicFightWeaponCap(ItemStack stack) {
    try {
        // 版ごとに正典 API を確認して1行差し替え（不明な版は false=fallback に倒す）。
        return EpicFightCompatProbe.itemHasWeaponCapability(stack);
    } catch (Throwable ignored) {       // LinkageError 含む全例外を握る
        return false;
    }
}
```

**実 ID の確認方法**（いずれかで、版ごとに）:

- `unzip -l WeaponsOfMiracles-*.jar | grep 'assets/weaponsofmiracles/.*\.json'`
- ゲーム内 `/give @s weaponsofmiracles:` のタブ補完
- jar を逆コンパイルしてレジストリ登録を確認

---

## 4. 「特異点」ボスへの段階組込

採用粒度は **「武器＋ムーブセットを段階解禁」**（`docs/epicfight/12 §5.5` と連動）。
`Singularity#stageWeaponFor(int)` を override し、`skillStage` ごとに WoM 武器へ。
武器付随の Epic Fight ムーブセットは `docs/epicfight/12 §3` の HumanoidMobPatch で
そのまま流用される。

```java
@Override
protected Item stageWeaponFor(int stage) {
    switch (stage) {
        case 0:  return ModItems.EXAMPLE_SWORD.get();                 // 基本武器
        case 1:  return womWeaponOr(List.of("solar"),                 Items.DIAMOND_SWORD);
        case 2:  return womWeaponOr(List.of("satsujin", "satujin"),   Items.NETHERITE_SWORD);
        default: return womWeaponOr(List.of("antitheus"),             Items.NETHERITE_AXE);
    }
}
```

- **WoM の27スキルは“本物実行”しない**（`docs/epicfight/12 §4-A` は実験的）。
  必要な「スキル相当効果」は `§4-B` の CombatBehaviors/カスタムアニメで再現し、
  `§5` の段階ゲートに載せる。
- 連携全体は起動時セルフチェック `EpicFightCompat.ENABLED`
  （`docs/epicfight/12 §STEP 2.6 (3)`）で囲み、**ミスマッチ時は連携 OFF・
  ボスは `docs/epicfight/12 §1` のバニラ実装で正常稼働**へ収束させる。

---

## 5. WoM コンテンツ概要（参照用・版で増減）

| 種別 | 概要 |
|---|---|
| 武器 | 複数（代表: Antitheus / Satsujin / Solar 等）。各々に専用アニメ/ムーブセット |
| スキル | 複数（チャージ/特殊技等。本書は**本物実行しない**＝フォールバック再現） |
| 防具 | 「Artifact」系（重量0・防御の一部を体力化 等の特性） |
| その他 | エンチャント/エンティティ等 |

正確な点数・登録名・素材は**版ごとに公式配布ページと jar で確認**。本書の固有名は
代表例であり、無ければ `womWeaponOr` が自動でバニラ武器へ降格する。

---

## 6. 検証

```bash
# 一致ペア確認（オフライン・最重要）
unzip -p WeaponsOfMiracles-*.jar META-INF/mods.toml | grep -A4 'modId[ ]*=[ ]*"epicfight"'
unzip -p epicfight-forge-*.jar    META-INF/mods.toml | grep -A3 'modId[ ]*=[ ]*"forge"'

# 本 Mod 側の静的検証（`docs/09` STEP4 と同一）
for f in $(find src -name '*.json'); do python3 -m json.tool "$f" >/dev/null 2>&1 || echo BAD:$f; done
./gradlew --version    # Gradle 8.1.1
```

- **本環境でできる**: WoM/Epic Fight jar からの版要求読取（オフライン）、本 Mod の
  静的検証、Gradle ラッパ確認。
- **本環境でできない**: `./gradlew build`（Forge/Cursemaven 遮断）、実機起動・
  ムーブセット・スキル・WoM アイテム解決 → **未検証**として正直に扱う（`docs/10`）。

---

## 7. よくある失敗

| 症状 | 原因 / 対処 |
|---|---|
| EF と WoM のミスマッチでクラッシュ | 版不一致。§1.1 の `unzip` でペア確定。連携を `EpicFightCompat.ENABLED`＋`try/catch(Throwable)` で囲む |
| WoM 未導入でクラッシュ | `ModList.isLoaded` ガード未実施 / `mandatory=true` にした → `false`（§2） |
| 武器が出ない/見た目だけで動かない | 版で登録名変化 or capability 不一致 → `womWeaponOr` の複数候補＋capability 確認で自動降格（§3） |
| EF 更新後に `NoSuchMethodError` 等 | 深い内部 API 直参照 → 安定エントリ＋例外境界（`docs/epicfight/12 §STEP 2.6 (2)`） |
| 配布物にライセンス問題 | WoM アセット/jar を同梱した → **ID 参照のみ**に徹する（§0） |

---

## 8. 参照

- `docs/epicfight/11-EpicFight戦闘スタイル.md` — Epic Fight の仕組み・武器 capability（datapack）
- `docs/epicfight/12-EpicFight特異点ボス.md` — ボス本体・HumanoidMobPatch・CombatBehaviors・段階ゲート
- `docs/09`（汎用追加手順）, `docs/10`（バグ/未検証の扱い）, `docs/01`（Forge/Java 前提）
- Weapons of Miracles: CurseForge / Modrinth の配布ページ（点数・登録名・依存は版で確認）
- Epic Fight Wiki: `https://epicfight-docs.readthedocs.io/`（API/Weapon Type は版で確認）

本書の固有名・点数・API 名は **Epic Fight / WoM の版**で変わる。最終的な根拠は
各 jar の `mods.toml`・公式配布ページ・起動ログとすること（憶測で「動作確認済」と書かない）。
