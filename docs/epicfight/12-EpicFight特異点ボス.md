# 12 - Epic Fight「特異点」ボス（段階的強化システム + プレイヤー行動の転用）

プレイヤー型スキンの強敵ボス **「特異点(Singularity)」** を、**段階的に強くなる**
システムとして実装する。さらに Epic Fight / **Weapons of Miracles (WoM)** の
プレイヤー行動・武器ムーブセットを敵に転用する手順を示す。

本書は **API 主軸**（コード片中心）。`docs/epicfight/11`（datapack 方式）の上位編に当たる。

---

## 0. 正直な前提（最初に必ず読む）

- 対象: **Epic Fight Forge 20.x / Minecraft 1.20.1**。版で内部 API が変わるため
  最終仕様は §11 の公式 wiki と**逆コンパイルで版ごとに確認**すること。
- **Epic Fight は「mob がプレイヤーの Skill 実行系を回す」設計ではない。**
  スキルは `SkillContainer`（= PlayerPatch 側）に紐づく。これを mob で駆動するのは
  **実験的・上級・版脆弱**で、版によっては **AccessTransformer / Mixin** が要る。
  → §4 に「実験的アプローチ」と **推奨フォールバック** の両方を示す。
- **本実行環境ではビルド/実機/描画/ムーブセット/Skill 駆動は検証不可**
  （Cursemaven / Forge maven がネットワークポリシーで遮断・headless）。
  保証できるのは「`src/` のバニラ実装の静的正しさ」まで。Epic Fight / WoM 連携部は
  **本書のコード片**であり、ビルドを壊さないため `src/` には入れていない（`docs/epicfight/11` 同方針）。
- WoM 連携は **任意ソフト依存**・**レジストリID参照のみ**（アセット非コピー＝
  ライセンス遵守）・**WoM 不在でもボスは動作**（バニラ武器へ自動フォールバック）。

---

## 1. 段階的強化システム（`src/` に実装済み・トリガ非依存）

ボス本体は **Epic Fight に依存せず**バニラ Forge だけで完結している（コンパイル可）。

| 要素 | 実体 |
|---|---|
| エンティティ登録 | `registry/ModEntities.java`（`SINGULARITY`） |
| ボス本体 | `entity/Singularity.java` |
| 属性登録 | `ExampleMod#onEntityAttributeCreation`（MOD バス） |
| 描画（プレイヤー型） | `client/SingularityRenderer.java` / `client/ClientSetup.java` |
| スポーンエッグ | `ModItems.SINGULARITY_SPAWN_EGG` |
| 手動ドライバ | `command/SingularityStageCommand.java` |
| スキン(64x64) | `assets/examplemod/textures/entity/singularity.png` |

### 設計の核
`Singularity` は同期データ `skillStage`(0..`MAX_STAGE`) を持つ（synced・NBT 永続・初期0）。
段階が上がると **属性強化** ＋ **装備武器** ＋ **行動選択層が使える抽象アクション集合**
が広がる（最終段＝全行動解禁）。

### トリガは「未定」— 起点は決め打ちしない
段階を進める**起点**（HP / 経過時間 / 撃破数 / 独自条件）は意図的に未実装。
「段階で強くなる事実」と**駆動 API・拡張ポイント**だけを提供する:

```java
// 駆動 API（外部・拡張ポイントから呼ぶ）
boss.getSkillStage();        // 現在段
boss.setSkillStage(2);       // 段を直接設定（範囲クランプ＋属性/武器/バー更新）
boss.advanceStage();         // 1 段進める

// 拡張ポイント（既定 no-op）。ここに「起点」を実装する。
@Override
protected void tickStageProgression() {
    // 例1: HP しきい値で段階を上げる
    float f = getHealth() / getMaxHealth();
    int want = f < 0.25F ? 3 : f < 0.5F ? 2 : f < 0.75F ? 1 : 0;
    if (want > getSkillStage()) advanceStage();

    // 例2: 経過 tick で上げる   if (tickCount % 1200 == 0) advanceStage();
    // 例3: 撃破数/フラグ等の独自条件で advanceStage();
}
```

動作確認用の**中立な手動ドライバ**（OP 権限）:

```
/singularity_stage next       最寄り64ブロックの特異点を 1 段進める
/singularity_stage set <0..3> 段を直接設定
```

> `tickStageProgression()` を override したサブクラスを別 EntityType として登録すれば
> 起点違いのボスを併存できる。「起点をどうするか」は後から差し替えるだけでよい。

### 行動選択層（具体実装・`src/` 側）
`Singularity` は毎ティック、相手の状態から抽象アクションを選び **段階でゲート**して
公開する（実際の移動/攻撃はバニラ Goal、択の意味づけが本層）:

| アクション | minStage | 選択条件（要約） |
|---|---|---|
| `APPROACH` | 0 | 間合いが遠い |
| `PRESSURE` | 1 | 近距離の基本攻め |
| `DEFEND` | 1 | 相手がスプリント突進 |
| `REPOSITION` | 2 | 相手がガード中（崩し前提の回り込み） |
| `SPECIAL` | 3 | 自 HP < 35%（切り札） |

未解禁段のアクションは一段下の安全行動へ自動フォールバック（`gate()`）。
`boss.getCurrentAction()` / `getCurrentActionTier()` で参照でき、**この出力を
Epic Fight のスキル/モーションへマッピングする**のが §5。

---

## 2. STEP — Epic Fight 連携の足場（build.gradle / mods.toml・手順のみ）

> ここからは Epic Fight / WoM 連携。**`src/` は変更しない**（依存不在でビルドが
> 壊れるため）。下記は導入環境で適用するコード片。

### STEP 2.1 — Epic Fight を `compileOnly` 依存に
`build.gradle`（`docs/epicfight/11 §6` と同じ Cursemaven を使用）:

```gradle
repositories {
    maven { url = 'https://cursemaven.com' }
}
dependencies {
    // API を呼ぶため compileOnly（実行時はユーザーが Epic Fight を導入）
    compileOnly fg.deobf("curse.maven:epic-fight-<projectId>:<fileId>")
}
```

`mods.toml` に **任意ソフト依存**（`docs/epicfight/11 §5` と同形・`mandatory=false`）:

```toml
[[dependencies.examplemod]]
    modId="epicfight"
    mandatory=false
    versionRange="[20,)"
    ordering="AFTER"
    side="BOTH"
```

### STEP 2.5 — Weapons of Miracles も任意ソフト依存に
WoM は Epic Fight アドオン（武器11・スキル27・防具4等／mc1.20.1-forge）。
**もう一段の任意ソフト依存**として追加（不在でもボスは動く）:

> **互換性（最重要・実導入前に必ず確認）**
> - **Epic Fight と WoM は「対応する版同士をペアで」揃える。** WoM は Epic Fight の
>   ハード依存アドオンで、特定の Epic Fight API（`AnimationRegistryEvent` 等）を前提に
>   ビルドされる。バラバラに最新を入れると**ロード不可/クラッシュ**する（既知事例多数）。
> - Epic Fight 1.20.1 は **Forge 下限**を要求する（直近版は概ね **≥ 47.4.4**、古い版は
>   ≥47.2.20）。本プロジェクトの `forge_version=47.4.10` は充足。下げないこと。
> - Java は **17 固定**（MC1.20.1/Forge47 共通。`CLAUDE.md` §1）。WoM/Epic Fight も同じ。
> - 正しいペアは導入環境の**起動ログ**で確定する（本実行環境では検証不可）。
> - 本 Mod 側は `mandatory=false`＋`ModList.isLoaded` ガードのため、**両者の有無や
>   版に関わらず examplemod 自体はロードできる**（連携機能だけが有効/無効になる）。
> - **→ 互換性を高める具体実装（版耐性＋一致ペア選定の手順）は STEP 2.6。**

```gradle
dependencies {
    compileOnly fg.deobf("curse.maven:weapons-of-miracles-<projectId>:<fileId>")
}
```

```toml
[[dependencies.examplemod]]
    modId="weaponsofmiracles"
    mandatory=false
    versionRange="[1,)"
    ordering="AFTER"
    side="BOTH"
```

WoM アイテムは **ID 参照のみ**（jar/アセットはコピーしない）。版差・ミスマッチに
強くするため、**複数候補ID＋Epic Fight 武器 capability の存在確認＋例外境界**で
解決する（単純な単一ID版より互換性が高い）:

```java
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * WoM 武器を「論理名 → 版ごとに変わりうる複数候補ID」で解決する。
 * - WoM 未導入 → fallback
 * - どの候補IDも存在しない → fallback（版差吸収）
 * - 存在するが Epic Fight 武器 capability を持たない/EF API 不一致 → fallback
 *   （ムーブセットが付かない“見た目だけ”を避け、ミスマッチでも安全に縮退）
 */
public static Item womWeaponOr(List<String> candidateIds, Item fallback) {
    if (!ModList.get().isLoaded("weaponsofmiracles")) return fallback;
    for (String id : candidateIds) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("weaponsofmiracles", id));
        if (it != null && hasEpicFightWeaponCap(new ItemStack(it))) return it;
    }
    return fallback;
}

/**
 * Epic Fight の武器 capability 有無を“防御的に”判定する。
 * EF の正確な取得 API は版依存。ここでは <b>例外境界</b>が肝で、API が変わって
 * NoSuchMethodError/NoClassDefFoundError/LinkageError 等が出ても連携機能を
 * 黙って無効化し、本 Mod を巻き込んで落とさない（＝ミスマッチ耐性）。
 */
private static boolean hasEpicFightWeaponCap(ItemStack stack) {
    try {
        // 版ごとに正典 API を確認して 1 行差し替える（例: EpicFightCapabilities
        //   .getItemStackCapability(stack) が weapon capability を返すか）。
        // 不明な版では「false（=fallback）」に倒すのが安全。
        return EpicFightCompatProbe.itemHasWeaponCapability(stack);
    } catch (Throwable ignored) {       // LinkageError 含む全例外を境界で握る
        return false;
    }
}
```

> **互換性のポイント**: 「存在チェックだけ」でなく **capability 確認＋例外境界**を
> 入れることで、EF と WoM がミスマッチ（IDはあるが EF API が食い違う）でも
> *見た目だけ装備して挙動が壊れる*事故を避け、自動でバニラ武器へ降格する。
> 候補IDは版で変わるため**論理名→複数候補**で持つ（下記 STEP 5.5）。

### STEP 2.6 — 互換性を高める実装方針（最重要・本書の核）

> **前提（正直に）**: Epic Fight と WoM は他者の Mod であり、両者“間”の互換性
> そのものは本 Mod から改変できない。**我々が高められるのは
> (1) 連携コードの版耐性（広い EF/WoM 版で壊れず縮退）と
> (2) 一致ペアを確実に選ぶ手順**の二つ。以下を必ず実施する。

**(1) 一致ペアの決定的な選定（オフラインで可能・唯一の本質的対策）**

WoM は Epic Fight のハード依存アドオン。WoM 各ビルドが要求する Epic Fight
バージョンは **WoM jar の `mods.toml`** に書いてある。ゲーム起動前に読める:

```bash
# WoM が要求する Epic Fight の versionRange を確認
unzip -p WeaponsOfMiracles-*.jar META-INF/mods.toml \
  | grep -A4 'modId[ ]*=[ ]*"epicfight"'

# Epic Fight が要求する Forge 下限を確認（本構成 47.4.10 が範囲内か）
unzip -p epicfight-forge-*.jar META-INF/mods.toml \
  | grep -A3 'modId[ ]*=[ ]*"forge"'
```

→ 表示された Epic Fight `versionRange` に**収まる Epic Fight ビルドを入れる**。
これで EF↔WoM は適合する（憶測でなく WoM 自身の宣言に従う）。

**(2) 防御的 Epic Fight API 利用（版耐性）**

- すべての EF 呼び出しは **境界で `try { ... } catch (Throwable)`**。API 変更時は
  連携機能だけ自動 OFF（本 Mod・ボスは生存＝§1 のバニラ実装で戦い続ける）。
- **安定エントリを優先**: datapack capabilities（`docs/epicfight/11`）と
  `EpicFightCapabilities.getEntityPatch()` 系。深い内部クラスの直参照は最小化。
- EF 連携は**独立ファイル**に隔離（`src/` に置かない＝§0）。EF 不在/不一致で
  コンパイル・ロードのどちらも本体に波及させない。

**(3) ミスマッチ隔離（起動時セルフチェック）**

```java
@Mod.EventBusSubscriber(modid = ExampleMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EpicFightCompat {
    public static boolean ENABLED = false;

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent e) {
        boolean ef  = ModList.get().isLoaded("epicfight");
        boolean wom = ModList.get().isLoaded("weaponsofmiracles");
        if (!ef) { log("Epic Fight 未導入 → 連携OFF（ボスはバニラ実装で動作）"); return; }
        try {
            // 連携に必須の EF クラス/メソッドが“この版に”あるか自己確認。
            Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities");
            ENABLED = true;
            log("Epic Fight 連携 ON" + (wom ? " / WoM 連携 ON" : " / WoM なし=バニラ武器"));
        } catch (Throwable t) {     // 版不一致 → 連携OFF（クラッシュさせない）
            ENABLED = false;
            log("Epic Fight API 不一致 → 連携OFF: " + t);
        }
    }
}
```

`SingularityPatch` 登録（§3 STEP6）や CombatBehaviors（§4-B）は
`if (!EpicFightCompat.ENABLED) return;` を先頭に置く。これで **EF/WoM が
どの版でも・ミスマッチでも、最悪「連携無効・ボスはバニラ実装で正常稼働」** に収束する。

**(4) 互換性マトリクス雛形（選定結果を必ず記録）**

| 役割 | 採用ビルド | 要求（jar mods.toml より） | 確認 |
|---|---|---|---|
| Minecraft | 1.20.1 | — | ✅ |
| Forge | 47.4.10 | Epic Fight 要求 ≥ ____ | ☐ |
| Epic Fight | epicfight-forge-__.__.__-1.20.1 | WoM 要求 versionRange ____ に収まる | ☐ |
| Weapons of Miracles | WeaponsOfMiracles-__.__ | EF 上記に一致 | ☐ |
| Java | 17（toolchain 固定） | 共通 | ✅ |

> 本実行環境では起動検証不可（ネット遮断・headless）。上記 `unzip` 手順は
> **オフラインで実施可能**＝最も確実な互換確認。実機ログでの最終確認は別環境で。

---

## 3. STEP 3 — プレイヤー armature の HumanoidMobPatch

mob にプレイヤーの biped armature と全武器モーションを与える Epic Fight パッチ。
`src/` の `Singularity` をそのまま patch 対象にする（**本書のコード片**）:

```java
// (Epic Fight 導入環境用。src には置かない)
public class SingularityPatch extends HumanoidMobPatch<Singularity> {

    public SingularityPatch() {
        super(Faction.NONE);
    }

    @Override
    public void initAnimator(Animator animator) {
        // プレイヤーと同じ移動/待機モーション
        animator.addLivingAnimation(LivingMotions.IDLE,   Animations.BIPED_IDLE);
        animator.addLivingAnimation(LivingMotions.WALK,    Animations.BIPED_WALK);
        animator.addLivingAnimation(LivingMotions.RUN,     Animations.BIPED_RUN);
        // 持っている武器の Epic Fight ムーブセットを使わせる
        this.updateMotion(false);
    }

    @Override
    public <M extends Model> M getEntityModel(Models<M> models) {
        return models.biped;                 // プレイヤー型 armature
    }

    @Override
    public StaticAnimation getHitAnimation(StunType stunType) {
        return Animations.BIPED_HIT_SHORT;
    }
}
```

EntityPatch の登録は **MOD バス**・`@Mod.EventBusSubscriber(bus = MOD)`（`docs/epicfight/11 §9`）:

```java
@Mod.EventBusSubscriber(modid = ExampleMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EpicFightCompat {
    @SubscribeEvent
    public static void onPatchRegistry(EntityPatchRegistryEvent event) {
        event.put(ModEntities.SINGULARITY.get(), SingularityPatch::new);
    }
}
```

> クラス/メソッド名（`HumanoidMobPatch` / `Animator` / `EntityPatchRegistryEvent` /
> `LivingMotions` / `Animations`）は**版で変わりうる**。コンパイルが通らなければ
> 逆コンパイルしたシグネチャに合わせて読み替えること。

---

## 4. STEP 4 —「全スキル」を mob で駆動（実験的）と推奨フォールバック

### 4-A. 実験的アプローチ（保証は部分的）
Epic Fight のスキルは `SkillContainer`（PlayerPatch 側）に属する。mob で本物の
27 スキルを実行するには、概念的には次が必要:

1. mob のパッチに **スキルコンテナ相当**を保持させる
   （`SkillContainer` を生成し、`PlayerPatch` 前提の参照を mob 側へ橋渡し）。
2. スキル発動の入口（`SkillContainer#sendExecuteRequest` 相当）を、プレイヤー入力
   ではなく**行動選択層（§5）から**叩く。
3. `PlayerPatch` 限定の `protected/private` へ触れる場合は **AccessTransformer**
   または **Mixin** が必要。

```java
// 想定形（版依存・要逆コンパイル確認・本環境未検証）
SkillContainer sc = new SkillContainer(/* mob patch を player patch 互換に橋渡し */);
sc.setSkill(EpicFightSkills.GUARD);
sc.sendExecuteRequest(/* ... */);   // ← 実機での挙動は版ごとに要検証
```

> このルートは **版脆弱で保証が部分的**。採用するなら「特定版で逆コンパイルし、
> SkillContainer の生成/実行に必要な前提を実測 → 不足は AT/Mixin」で確定させる。
> 本書はここまで（実機未検証）と正直に明記する。

### 4-B. 推奨フォールバック（確実・採用方針）
**スキルそのものを回さず、必要な「スキル相当の効果」を CombatBehaviors と
カスタム攻撃アニメで再現**する。武器付随ムーブセット（§3 で取得）＋これで
プレイヤー相当の戦い方は十分に再現できる。

```java
// SingularityPatch 内: 段階ゲートした CombatBehaviors
this.combatBehaviors = CombatBehaviors.<SingularityPatch>builder()
    .newBehaviorSeries() // 近接コンボ（PRESSURE 相当）
        .behavior(CombatBehaviors.Behavior.<SingularityPatch>builder()
            .predicate(p -> withinStage(p, 1) && action(p) == Action.PRESSURE)
            .animationBehavior(Animations.BIPED_AUTO_ATTACK_COMBO))
    .newBehaviorSeries() // ガード崩し（REPOSITION 相当・stage>=2）
        .behavior(CombatBehaviors.Behavior.<SingularityPatch>builder()
            .predicate(p -> withinStage(p, 2) && action(p) == Action.REPOSITION)
            .animationBehavior(Animations.BIPED_GUARD_BREAK))
    .newBehaviorSeries() // 切り札（SPECIAL 相当・stage>=3）
        .behavior(CombatBehaviors.Behavior.<SingularityPatch>builder()
            .predicate(p -> withinStage(p, 3) && action(p) == Action.SPECIAL)
            .animationBehavior(Animations.BIPED_SPECIAL))
    .build();
```

---

## 5. STEP 5 — 段階ゲート行動選択層 → Epic Fight へのマッピング（具体実装）

`src/` の `Singularity.skillStage` と `getCurrentAction()` を Epic Fight 行動へ橋渡す。
**HP では段階を切替えない**（`skillStage` 起点はトリガ非依存・§1）。

```java
// SingularityPatch 補助（本書のコード片）
static Singularity self(SingularityPatch p) { return p.getOriginal(); }
static Singularity.Action action(SingularityPatch p) { return self(p).getCurrentAction(); }
static boolean withinStage(SingularityPatch p, int min) { return self(p).getSkillStage() >= min; }
```

段階 → 使用可能スキル群（フォールバック実装＝アニメ/挙動の集合）の対応例:

| skillStage | 解禁される戦い方（行動選択層の minStage に整合） |
|---|---|
| 0 | 基本コンボのみ（`APPROACH`/`PRESSURE`） |
| 1 | + 受け（`DEFEND`） |
| 2 | + ガード崩し・回り込み（`REPOSITION`） |
| 3 | + 切り札（`SPECIAL`）= 全解禁 |

効率選択は `Singularity#chooseAction` が既に実装済み（相手の `isBlocking()` /
スプリント / 間合い / 自 HP で段階内の最適手を選ぶ）。Epic Fight 側は
その結果を **predicate に載せるだけ**でよい（上記 §4-B）。

---

## 5.5 STEP — WoM 武器の段階解禁（具体実装・推奨採用粒度）

採用粒度は **「武器＋ムーブセットを段階解禁」**。`Singularity#stageWeaponFor(int)` を
override し、`skillStage` ごとに WoM 武器へ差し替える。武器の Epic Fight
ムーブセットは §3 の HumanoidMobPatch でそのまま流用される。

**論理名 → 複数候補ID**で持ち、STEP 2.5 の `womWeaponOr`（capability 確認＋
例外境界付き）で解決する。候補IDは WoM の版で変わるため**複数並べる**こと
（実IDは STEP 2.6 の `unzip` か `/give weaponsofmiracles:` 補完で確認）:

```java
// (Epic Fight + WoM 導入環境用。src の Singularity を継承して別 EntityType 登録、
//  または stageWeaponFor を直接 override)
@Override
protected Item stageWeaponFor(int stage) {
    switch (stage) {
        case 0:  return ModItems.EXAMPLE_SWORD.get();              // 基本武器
        case 1:  return womWeaponOr(List.of("solar"),               Items.DIAMOND_SWORD);
        case 2:  return womWeaponOr(List.of("satsujin", "satujin"), Items.NETHERITE_SWORD);
        default: return womWeaponOr(List.of("antitheus"),           Items.NETHERITE_AXE);
    }
}
```

- **版耐性**: WoM 不在／候補ID全滅／capability 不一致のいずれでも
  `womWeaponOr` が自動でバニラ武器へ降格 → **どの EF/WoM 版でもクラッシュしない**。
- **WoM の27スキルは本物実行しない**（§4-A は実験的）。必要な「スキル相当効果」は
  §4-B の CombatBehaviors/カスタムアニメで再現し、§5 の段階ゲートに載せる。
- 武器の capability（ムーブセット）は WoM 側が定義済み。本 Mod は**参照するだけ**
  （アセット非コピー＝ライセンス遵守）。
- 連携全体は §STEP 2.6 (3) の `EpicFightCompat.ENABLED` で囲み、ミスマッチ時は
  連携 OFF・ボスは §1 のバニラ実装で正常稼働に収束させる。

---

## 6. ボスバー / 段階遷移

`Singularity` は `ServerBossEvent`（紫・PROGRESS）を持ち、バー名に段を表示
（`特異点 [n/4]`）、`customServerAiStep` で HP 比を `setProgress`。
段が上がると `setSkillStage` がバー名・属性・武器を一括更新する。
遷移演出を足すなら `setSkillStage` の末尾でパーティクル/効果音を鳴らす。

---

## 7. 検証

```bash
# JSON 全件パース（lang 含む）
for f in $(find src -name '*.json'); do
  python3 -m json.tool "$f" >/dev/null 2>&1 || echo "BAD JSON: $f"
done

# id 一貫性
grep -n 'singularity' src/main/resources/assets/examplemod/lang/*.json
ls src/main/resources/assets/examplemod/textures/entity/

./gradlew --version          # Gradle 8.1.1
```

**本実行環境で検証できたこと**: `src/` のバニラ実装の静的正しさ（JSON / id / 構成）、
Gradle ラッパ。
**検証できないこと（別環境で要確認）**: `./gradlew build`（Forge maven 遮断）、
実機起動・プレイヤー型描画・ボスバー・コマンド、Epic Fight ムーブセット、WoM 武器、
§4-A の Skill 駆動。→ これらは**未検証**として正直に扱う（`docs/07`/`docs/10` 方針）。

---

## 8. チェックリスト

- [ ] `src/` のバニラボスがコンパイル前提を満たす（DeferredRegister 様式・属性は MOD バス）
- [ ] `skillStage` API（get/set/advance）と拡張ポイント `tickStageProgression()` がある
- [ ] 行動選択層が段階ゲートされ `getCurrentAction()` で参照できる
- [ ] スポーンエッグ・lang(en/ja)・64x64 テクスチャ・レンダラが揃っている
- [ ] Epic Fight 連携は `docs/epicfight/12` コード片のみ（`src/` 非依存・ビルド非破壊）
- [ ] WoM は任意ソフト依存・**複数候補ID**・capability 確認・**例外境界**・不在で降格
- [ ] EF↔WoM の一致ペアを STEP 2.6 (1) の `unzip` で確定し、互換マトリクスを記録
- [ ] 全 EF 呼び出しが `EpicFightCompat.ENABLED` ＋ `try/catch(Throwable)` 配下
- [ ] ミスマッチ時に「連携OFF・ボスはバニラ実装で稼働」へ収束する設計
- [ ] 27スキルの本物実行は実験的扱い、採用はフォールバック（§4-B）
- [ ] 実機/ムーブセット/Skill 駆動は別環境で確認 or 未検証と明記

## 9. よくある失敗

| 症状 | 原因 |
|---|---|
| ビルドで Epic Fight クラスが見つからない | 連携コードを `src/` に置いた → `docs/epicfight/12` のコード片に留める |
| WoM 未導入でクラッシュ | `ModList.isLoaded` ガード未実施 / `mandatory=true` にした |
| EF と WoM のミスマッチでクラッシュ | EF↔WoM 版不一致。STEP 2.6 (1) の `unzip` でペア確定。連携を `EpicFightCompat.ENABLED`＋`try/catch(Throwable)` で囲む |
| EF 更新後に NoSuchMethodError 等 | 深い内部 API 直参照。安定エントリ＋例外境界へ（STEP 2.6 (2)）。連携OFF で本体は生存 |
| `EntityPatchRegistryEvent` が呼ばれない | FORGE バスに登録した → **MOD バス**で登録（§3） |
| スキンが紫黒 | `textures/entity/singularity.png` 不在/名前不一致 |
| 段が上がらない | 起点未実装は仕様（§1）。`tickStageProgression()` か コマンドで駆動 |
| WoM 武器が出ない/見た目だけ動かない | 版で登録名変化 or capability 不一致 → `womWeaponOr` の複数候補＋capability 確認で自動降格中（STEP 2.5） |

## 10. 付録: なぜ機械学習（模倣学習）を採らないか

「プレイヤーの動きを学習させる」案は Minecraft Mod 実装としては不適:
観測/報酬設計・学習基盤・推論レイテンシ・再現性・配布サイズの全てがコストに見合わず、
**ルール/有限状態機械 + Epic Fight の既存ムーブセット**で「強くて自然な敵」は十分作れる。
本書の段階ゲート行動選択層（§1/§5）がその実装。

## 11. 公式リファレンス（版ごとに必ず確認）

- Epic Fight Wiki: `https://epicfight-docs.readthedocs.io/`
  - API（独自パッチ/スキル/アニメ。`EntityPatchRegistryEvent` は **MOD バス**登録）
  - Weapon Type Editor / Item Capability（`docs/epicfight/11` と共通）
- Weapons of Miracles: CurseForge / Modrinth の配布ページ（武器登録名は版で確認）
- 関連: `docs/epicfight/11`（datapack 方式の戦闘スタイル割当）, `docs/09`（汎用追加手順）,
  `docs/10`（バグ/未検証の扱い）

本書の `src/` 実装は 1.20.1 標準 API のみで静的検証済み。Epic Fight / WoM 連携部は
版依存のため、上記 wiki と逆コンパイルを最終的な根拠とすること。
