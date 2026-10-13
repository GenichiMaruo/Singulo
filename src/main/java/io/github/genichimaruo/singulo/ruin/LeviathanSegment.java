package io.github.genichimaruo.singulo.ruin;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

/**
 * エルゴ球の大蛇の体の一節（エンダードラゴンの部位と同じ作り）。当たり判定だけを持ち、受けたダメージは本体へ渡す。
 * 位置は本体が毎 tick、頭の通った跡に沿って並べ直す。保存しない。
 */
public class LeviathanSegment extends PartEntity<ErgosphereLeviathan> {
    /** 頭から数えた番号（0 が首のすぐ後ろ）。 */
    public final int index;
    /** 本体へ渡すダメージの倍率（胴は硬く、尾の光る芯は弱い）。 */
    public final float damageFactor;
    private final EntityDimensions size;

    public LeviathanSegment(ErgosphereLeviathan parent, int index, float width, float damageFactor) {
        super(parent);
        this.index = index;
        this.damageFactor = damageFactor;
        this.size = EntityDimensions.scalable(width, width);
        refreshDimensions();
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() {
        return true;
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().hurtPart(this, source, amount);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
