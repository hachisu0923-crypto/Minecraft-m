package com.example.examplemod;

import com.example.examplemod.registry.ModBlocks;
import com.example.examplemod.registry.ModCreativeTabs;
import com.example.examplemod.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// @Mod の引数は mods.toml / assets / data のフォルダ名と完全一致させること。
@Mod(ExampleMod.MOD_ID)
public class ExampleMod {

    public static final String MOD_ID = "examplemod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ExampleMod() {
        // Mod 専用イベントバス（登録・初期化はここ）
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 各 DeferredRegister を Mod バスに接続
        ModItems.REGISTER.register(modEventBus);
        ModBlocks.REGISTER.register(modEventBus);
        ModCreativeTabs.REGISTER.register(modEventBus);

        LOGGER.info("{} initialized", MOD_ID);
    }
}
