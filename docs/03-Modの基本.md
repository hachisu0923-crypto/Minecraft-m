# 03 - Mod の基本

## @Mod とエントリポイント

`@Mod("examplemod")` を付けたクラスが Mod の入口（`ExampleMod.java`）。
引数の文字列 = mod id。これが `mods.toml` の `modId` と一致しないと Forge が読み込めない。

```java
@Mod(ExampleMod.MOD_ID)
public class ExampleMod {
    public static final String MOD_ID = "examplemod";
    public ExampleMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.REGISTER.register(modEventBus);
        // ... 登録クラスを足したらここに追加
    }
}
```

## 2 つのイベントバス（混同注意）

| バス | 取得方法 | 用途 |
|---|---|---|
| **Mod バス** | `FMLJavaModLoadingContext.get().getModEventBus()` | 登録・初期化・クライアントセットアップ（ライフサイクル） |
| **Forge バス** | `MinecraftForge.EVENT_BUS` | ゲーム中イベント（ブロック破壊、tick、ダメージ等） |

- `DeferredRegister.register(modEventBus)` は **Mod バス**
- プレイ中のゲームイベントを購読するなら **Forge バス**（`@Mod.EventBusSubscriber` か `MinecraftForge.EVENT_BUS.register`）

## ライフサイクルイベント（Mod バス）

| イベント | 用途 |
|---|---|
| `FMLCommonSetupEvent` | 共通初期化（ネットワーク登録など）。`event.enqueueWork(...)` でスレッド安全に |
| `FMLClientSetupEvent` | クライアント専用（レンダラ/画面登録）。サーバでは呼ばれない |
| `RegisterEvent` / `DeferredRegister` | レジストリ登録（通常は DeferredRegister を使う） |
| `BuildCreativeModeTabContentsEvent` | 既存タブへの追加（自前タブなら不要） |

購読例:
```java
@Mod.EventBusSubscriber(modid = ExampleMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) { /* ... */ }
}
```

## DeferredRegister — 登録の中心概念

`new` した Block/Item を直接使うと、レジストリ登録タイミングがずれてクラッシュする。
**必ず `DeferredRegister` で「あとで生成する」よう予約**し、`RegistryObject<T>` 経由で参照する。

```java
public static final DeferredRegister<Item> REGISTER =
        DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MOD_ID);

public static final RegistryObject<Item> EXAMPLE_ITEM =
        REGISTER.register("example_item", () -> new Item(new Item.Properties()));
```

- 値が必要なときに `EXAMPLE_ITEM.get()`（クラスロード時に `.get()` しない）
- 新カテゴリの登録レジストリ早見:

| 追加したいもの | レジストリ |
|---|---|
| アイテム | `ForgeRegistries.ITEMS` |
| ブロック | `ForgeRegistries.BLOCKS`（+ Item に BlockItem） |
| クリエイティブタブ | `Registries.CREATIVE_MODE_TAB`（`net.minecraft.core.registries`） |
| エンティティ | `ForgeRegistries.ENTITY_TYPES` |
| ブロックエンティティ | `ForgeRegistries.BLOCK_ENTITY_TYPES` |
| 音 | `ForgeRegistries.SOUND_EVENTS` |
| エンチャント | `ForgeRegistries.ENCHANTMENTS` |
| ポーション/エフェクト | `ForgeRegistries.MOB_EFFECTS` |

新カテゴリを足す手順:
1. `registry/ModXxx.java` を作り `DeferredRegister.create(<該当レジストリ>, MOD_ID)`
2. `RegistryObject` で要素を `register("name", () -> new ...)`
3. `ExampleMod` コンストラクタで `ModXxx.REGISTER.register(modEventBus)` を追加
4. 必要なアセット（モデル/lang/テクスチャ等）を `docs/06` のパス規約で用意

## マッピングについて（任意）

既定は official Mojang マッピング（`gradle.properties` の `mapping_channel=official`）。
パラメータ名や javadoc が欲しい場合は Parchment にできる:

```
mapping_channel=parchment
mapping_version=2023.09.03-1.20.1
```

加えて `settings.gradle` の pluginManagement/repositories と `build.gradle` に
ParchmentMC のプラグイン/リポジトリ追加が必要（ネットワーク必須）。
不要な複雑さを避けるため、まずは official のままで進めてよい。
