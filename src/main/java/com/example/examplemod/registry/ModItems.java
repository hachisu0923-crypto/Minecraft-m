package com.example.examplemod.registry;

import com.example.examplemod.ExampleMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// アイテムを追加するときはこのクラスをコピー元にする。
// 手順の詳細は docs/04-アイテム追加.md を参照。
public final class ModItems {

    public static final DeferredRegister<Item> REGISTER =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MOD_ID);

    public static final RegistryObject<Item> EXAMPLE_ITEM =
            REGISTER.register("example_item", () -> new Item(new Item.Properties()));

    // 武器の例。Epic Fight 戦闘スタイルの割り当て先になる（docs/epicfight/11 参照）。
    // SwordItem(Tier, 攻撃力補正, 攻撃速度補正, Properties)。1.20.1 のシグネチャ。
    public static final RegistryObject<Item> EXAMPLE_SWORD =
            REGISTER.register("example_sword",
                    () -> new SwordItem(Tiers.IRON, 3, -2.4F, new Item.Properties()));

    // ボス「特異点」のスポーンエッグ。ForgeSpawnEggItem は EntityType を遅延参照する。
    public static final RegistryObject<Item> SINGULARITY_SPAWN_EGG =
            REGISTER.register("singularity_spawn_egg",
                    () -> new ForgeSpawnEggItem(ModEntities.SINGULARITY, 0x1A0030, 0x9B30FF,
                            new Item.Properties()));

    private ModItems() {
    }
}
