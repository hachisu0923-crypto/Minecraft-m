package com.example.examplemod.registry;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.Singularity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// エンティティを追加するときはこのクラスをコピー元にする（ModItems と同じ DeferredRegister 様式）。
// 属性は ExampleMod の EntityAttributeCreationEvent で登録する点に注意。
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MOD_ID);

    // 段階的に強くなるボス「特異点(Singularity)」。プレイヤー型モデルで描画（client パッケージ）。
    public static final RegistryObject<EntityType<Singularity>> SINGULARITY =
            REGISTER.register("singularity", () -> EntityType.Builder
                    .of(Singularity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(3)
                    .build("singularity"));

    private ModEntities() {
    }
}
