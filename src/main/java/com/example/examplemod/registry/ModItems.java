package com.example.examplemod.registry;

import com.example.examplemod.ExampleMod;
import net.minecraft.world.item.Item;
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

    private ModItems() {
    }
}
