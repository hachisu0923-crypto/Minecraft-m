package com.example.examplemod.command;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.Singularity;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

/**
 * 段階を進める「起点」が未定のため用意する<b>中立な手動ドライバ</b>（動作確認用・OP 権限）。
 *
 * <pre>
 *   /singularity_stage next        最寄りの特異点を 1 段進める
 *   /singularity_stage set &lt;n&gt;     最寄りの特異点を段 n に設定
 * </pre>
 *
 * 将来 HP/時間/撃破等の起点を組むときは {@link Singularity#tickStageProgression()} に実装する。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MOD_ID)
public final class SingularityStageCommand {

    private SingularityStageCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("singularity_stage")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("next")
                                .executes(ctx -> apply(ctx.getSource(), -1)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("stage", IntegerArgumentType.integer(0, Singularity.MAX_STAGE))
                                        .executes(ctx -> apply(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "stage"))))));
    }

    private static int apply(CommandSourceStack source, int stage) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        List<Singularity> found = level.getEntitiesOfClass(Singularity.class,
                new AABB(pos.subtract(64.0D, 64.0D, 64.0D), pos.add(64.0D, 64.0D, 64.0D)));
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("64ブロック以内に特異点(Singularity)がいません"));
            return 0;
        }
        Singularity boss = found.stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(pos)))
                .orElseThrow();
        if (stage < 0) {
            boss.advanceStage();
        } else {
            boss.setSkillStage(stage);
        }
        int now = boss.getSkillStage();
        source.sendSuccess(() -> Component.literal("特異点 skillStage -> " + now), true);
        return 1;
    }
}
