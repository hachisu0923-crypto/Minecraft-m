package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.Singularity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * 「特異点」をプレイヤー型モデル（{@link ModelLayers#PLAYER}）で描画する。
 * テクスチャは 64x64 のプレースホルダ「スキン」。装備武器は {@link ItemInHandLayer} で表示。
 */
public class SingularityRenderer extends HumanoidMobRenderer<Singularity, PlayerModel<Singularity>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MOD_ID, "textures/entity/singularity.png");

    public SingularityRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(Singularity entity) {
        return TEXTURE;
    }
}
