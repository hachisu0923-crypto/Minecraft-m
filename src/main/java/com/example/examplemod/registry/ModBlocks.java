package com.example.examplemod.registry;

import com.example.examplemod.ExampleMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// ブロックを追加するときはこのクラスをコピー元にする。
// Block 本体と、その BlockItem の両方を登録する点に注意。
// 手順の詳細は docs/05-ブロック追加.md を参照。
public final class ModBlocks {

    public static final DeferredRegister<Block> REGISTER =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ExampleMod.MOD_ID);

    public static final RegistryObject<Block> EXAMPLE_BLOCK =
            REGISTER.register("example_block", () -> new Block(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(3.0F, 3.0F)
                            .requiresCorrectToolForDrops()));

    // BlockItem は Item レジストリ側に登録する（同じ id を使う）
    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM =
            ModItems.REGISTER.register("example_block",
                    () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties()));

    private ModBlocks() {
    }
}
