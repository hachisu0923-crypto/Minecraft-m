package com.example.examplemod.registry;

import com.example.examplemod.ExampleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

// 1.20.1 のクリエイティブタブは独自レジストリで登録する。
// 新しいアイテム/ブロックは displayItems の中に output.accept(...) で追加する。
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExampleMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB =
            REGISTER.register("example_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ExampleMod.MOD_ID + ".example_tab"))
                    .icon(() -> new ItemStack(ModItems.EXAMPLE_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.EXAMPLE_ITEM.get());
                        output.accept(ModBlocks.EXAMPLE_BLOCK_ITEM.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
