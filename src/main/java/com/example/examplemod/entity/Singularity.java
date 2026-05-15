package com.example.examplemod.entity;

import com.example.examplemod.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * 段階的に強くなるボス「特異点(Singularity)」。
 *
 * <p>設計の核は {@code skillStage}(0..{@link #MAX_STAGE}) に応じた段階強化:
 * 属性スケーリング・装備武器・行動選択層が使える抽象アクション集合が広がる。</p>
 *
 * <p><b>段階を進める「起点」は意図的に未実装（トリガ非依存）。</b>
 * HP/経過時間/撃破数/コマンド等の起点は今後決めるため、ここでは
 * 「段階で強くなる事実」と駆動 API・拡張ポイントのみを提供する:</p>
 * <ul>
 *   <li>駆動 API: {@link #setSkillStage(int)} / {@link #advanceStage()} / {@link #getSkillStage()}</li>
 *   <li>拡張ポイント: {@link #tickStageProgression()}（既定 no-op。ここに起点を実装する）</li>
 *   <li>中立な手動ドライバ: コマンド {@code /singularity_stage}（動作確認用）</li>
 * </ul>
 *
 * <p>Epic Fight / Weapons of Miracles 連携（プレイヤー行動の転用・武器ムーブセットの
 * 段階解禁・行動選択層の Epic Fight スキルへのマッピング）は本体に依存を持たせず
 * {@code docs/12-EpicFight特異点ボス.md} にコード片として記載する。</p>
 */
public class Singularity extends Monster {

    /** 段階の最大値（段は 0..MAX_STAGE の MAX_STAGE+1 段）。 */
    public static final int MAX_STAGE = 3;

    private static final EntityDataAccessor<Integer> DATA_SKILL_STAGE =
            SynchedEntityData.defineId(Singularity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent =
            new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    private Action currentAction = Action.APPROACH;

    public Singularity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.bossEvent.setName(this.stageTitle(0));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1D, true));
        this.goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 1.0D, 32.0F));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SKILL_STAGE, 0);
    }

    // ---- 段階駆動 API（トリガ非依存。外部/拡張ポイントから呼ぶ）-------------------

    public int getSkillStage() {
        return this.entityData.get(DATA_SKILL_STAGE);
    }

    public void setSkillStage(int stage) {
        int s = Mth.clamp(stage, 0, MAX_STAGE);
        this.entityData.set(DATA_SKILL_STAGE, s);
        if (!this.level().isClientSide) {
            this.applyStageScaling(s);
            this.applyStageWeapon(s);
            this.bossEvent.setName(this.stageTitle(s));
        }
    }

    public void advanceStage() {
        this.setSkillStage(this.getSkillStage() + 1);
    }

    /**
     * 段階を進める「起点」を実装する拡張ポイント。<b>既定は何もしない（トリガ非依存）。</b>
     * 例: HP しきい値・経過時間・撃破数・独自条件で {@link #advanceStage()} を呼ぶ実装に
     * 差し替える。起点の設計指針と Epic Fight 連携は {@code docs/12} を参照。
     */
    protected void tickStageProgression() {
    }

    // ---- 段階スケーリング ---------------------------------------------------------

    private void applyStageScaling(int stage) {
        this.setAttr(Attributes.MAX_HEALTH, 120.0D + stage * 80.0D);
        this.setAttr(Attributes.ATTACK_DAMAGE, 8.0D + stage * 5.0D);
        this.setAttr(Attributes.MOVEMENT_SPEED, 0.28D + stage * 0.02D);
        this.setAttr(Attributes.ARMOR, stage * 5.0D);
        this.setAttr(Attributes.ATTACK_KNOCKBACK, 1.0D + stage * 0.5D);
        this.setAttr(Attributes.KNOCKBACK_RESISTANCE, Math.min(1.0D, 0.4D + stage * 0.2D));
        this.setAttr(Attributes.FOLLOW_RANGE, 48.0D);
        this.setHealth(this.getMaxHealth());
    }

    private void setAttr(Attribute attribute, double value) {
        AttributeInstance inst = this.getAttribute(attribute);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    // ---- 段階 → 武器テーブル（docs/12 で Weapons of Miracles の武器IDへ差替える差込口）----

    private void applyStageWeapon(int stage) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(this.stageWeaponFor(stage)));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    /**
     * {@code skillStage} ごとの装備武器。<b>override 用の差込口。</b>
     * 本体はバニラ武器でプレースホルダ。docs/12 では Weapons of Miracles の
     * 武器（Solar / Satsujin / Antitheus 等）を {@code ModList.isLoaded} ガード付きで
     * ID 参照して返すよう override する（WoM 不在時はここへフォールバック）。
     */
    protected Item stageWeaponFor(int stage) {
        switch (stage) {
            case 0:
                return ModItems.EXAMPLE_SWORD.get();
            case 1:
                return Items.DIAMOND_SWORD;
            case 2:
                return Items.NETHERITE_SWORD;
            default:
                return Items.NETHERITE_AXE;
        }
    }

    // ---- 行動選択層（具体実装）: 段階でゲートし、相手の状態で段階内の最適手を選ぶ -------

    public enum Action {
        /** 接近（間合い詰め）。全段共通。 */
        APPROACH(0),
        /** 攻勢（近距離は基本攻め）。 */
        PRESSURE(1),
        /** 受け（突進の受け止め）。 */
        DEFEND(1),
        /** 回り込み（ガード崩し前提の位置取り）。 */
        REPOSITION(2),
        /** 切り札（追い込まれた時の特殊行動）。 */
        SPECIAL(3);

        public final int minStage;

        Action(int minStage) {
            this.minStage = minStage;
        }
    }

    public Action getCurrentAction() {
        return this.currentAction;
    }

    public int getCurrentActionTier() {
        return this.currentAction.minStage;
    }

    private Action chooseAction(LivingEntity target) {
        int stage = this.getSkillStage();
        double dist = this.distanceTo(target);
        boolean targetGuarding = target.isBlocking();
        boolean targetRushing = target.isSprinting() && dist < 6.0D;
        float hpFrac = this.getHealth() / this.getMaxHealth();

        Action desired;
        if (dist > 6.0D) {
            desired = Action.APPROACH;
        } else if (targetGuarding && stage >= Action.REPOSITION.minStage) {
            desired = Action.REPOSITION;
        } else if (hpFrac < 0.35F && stage >= Action.SPECIAL.minStage) {
            desired = Action.SPECIAL;
        } else if (targetRushing && stage >= Action.DEFEND.minStage) {
            desired = Action.DEFEND;
        } else {
            desired = Action.PRESSURE;
        }
        return this.gate(desired);
    }

    /** 段階未解禁のアクションは一段下の安全行動へフォールバックする。 */
    private Action gate(Action action) {
        if (action.minStage <= this.getSkillStage()) {
            return action;
        }
        return this.getSkillStage() >= Action.PRESSURE.minStage ? Action.PRESSURE : Action.APPROACH;
    }

    // ---- ティック / ボスバー / 永続化 --------------------------------------------

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        this.tickStageProgression();
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            this.currentAction = this.chooseAction(target);
        } else {
            this.currentAction = Action.APPROACH;
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData data,
                                        @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data, tag);
        this.setPersistenceRequired();
        this.setSkillStage(this.getSkillStage());
        return result;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SkillStage", this.getSkillStage());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setSkillStage(tag.getInt("SkillStage"));
    }

    private Component stageTitle(int stage) {
        return Component.translatable("entity.examplemod.singularity")
                .append(" [" + (stage + 1) + "/" + (MAX_STAGE + 1) + "]");
    }
}
