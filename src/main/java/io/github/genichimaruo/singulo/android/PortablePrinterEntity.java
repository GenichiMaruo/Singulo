package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 地面に置いた携帯型アンドロイド印刷機（0.21.0。{@link PortableAndroidPrinterItem}）。
 * <ol>
 * <li>開く（{@link #OPEN_TICKS}）: カバンのふたが真ん中の継ぎ目からパカっと開き、中から印刷面がせり上がる</li>
 * <li>印刷（{@link RuinAndroid#PRINT_TICKS}）: 印刷面の上でアンドロイドを印刷する（組立ステーションと同じ、下から積み上がる印刷）</li>
 * <li>閉じる（{@link #CLOSE_TICKS}）: 印刷面が沈み、ふたが閉じる</li>
 * <li>持ち物へ戻る: 使った人の持ち物へ（いない・いっぱいなら、その場に落とす）</li>
 * </ol>
 * 印刷したアンドロイドは、使った人の護衛で、ステーションを持たない（身寄りのない）アンドロイドになり、追従モードでついて歩く。
 * 印刷機に自己修理キットのストックがあれば、アンドロイドの枠に入るだけ持たせ、装甲板があれば積む。
 * 上に乗れる（印刷面にアンドロイドが立つ）。叩いても壊れない。
 */
public class PortablePrinterEntity extends Entity {
    public static final int OPEN_TICKS = 16, CLOSE_TICKS = 16;
    /** 印刷が終わってから閉じ始めるまで（アンドロイドが降りる間）。 */
    public static final int STEP_OFF_TICKS = 12;
    public static final int PRINT_START = OPEN_TICKS;
    public static final int PRINT_END = PRINT_START + RuinAndroid.PRINT_TICKS;
    public static final int CLOSE_START = PRINT_END + STEP_OFF_TICKS;
    public static final int DONE = CLOSE_START + CLOSE_TICKS;
    /** 印刷面の高さ（ブロック。上に立つアンドロイドの足もと）。 */
    public static final float BED = 4 / 16F;

    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(PortablePrinterEntity.class, EntityDataSerializers.INT);
    /** 置いた印刷機（描画で Mk1・Mk2 の絵を選ぶ）。 */
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(PortablePrinterEntity.class,
            EntityDataSerializers.ITEM_STACK);

    private ItemStack printer = ItemStack.EMPTY;
    @Nullable
    private UUID owner;
    @Nullable
    private AndroidKind kind;
    @Nullable
    private UUID android;
    /** 印刷する組（自己修理キットと装甲板をこの組から渡す）。 */
    private int unit;

    public PortablePrinterEntity(EntityType<? extends PortablePrinterEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    PortablePrinterEntity(Level level, Vec3 at, float yaw, ItemStack printer, UUID owner, @Nullable AndroidKind kind, int unit) {
        this(SinguloEntities.PORTABLE_PRINTER.get(), level);
        moveTo(at.x, at.y, at.z, yaw, 0);
        setPrinter(printer);
        this.owner = owner;
        this.kind = kind;
        this.unit = unit;
    }

    private void setPrinter(ItemStack stack) {
        printer = stack;
        entityData.set(ITEM, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    /** 置いた印刷機の種類（描画用。クライアントにも届く）。 */
    public ItemStack shownItem() {
        return entityData.get(ITEM);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(AGE, 0);
        entityData.define(ITEM, ItemStack.EMPTY);
    }

    /** 置いてからの時間（tick）。描画の開き具合はここから決まる。 */
    public int age() {
        return entityData.get(AGE);
    }

    /** ふたと印刷面の開き具合（0 閉じている 〜 1 開いている）。 */
    public float openness(float partialTick) {
        float t = age() + partialTick;
        float k;
        if (t < OPEN_TICKS) {
            k = t / OPEN_TICKS;
        } else if (t < CLOSE_START) {
            k = 1;
        } else {
            k = 1 - Math.min(1, (t - CLOSE_START) / CLOSE_TICKS);
        }
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    public ItemStack printer() {
        return printer;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        int age = age() + 1;
        entityData.set(AGE, age);
        if (age == 1) {
            level.playSound(null, blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.6F, 1.6F);
        } else if (age == PRINT_START) {
            print(level);
        } else if (age == PRINT_END) {
            stepOff(level);
        } else if (age == CLOSE_START) {
            level.playSound(null, blockPosition(), SoundEvents.CHEST_CLOSE, SoundSource.PLAYERS, 0.6F, 1.6F);
        } else if (age >= DONE) {
            giveBack(level);
        }
    }

    /** 印刷面の上でアンドロイドを印刷する: 使った人の護衛で、ステーションを持たず（身寄りがなく）、追従モード。 */
    private void print(ServerLevel level) {
        if (kind == null) {
            return;
        }
        RuinAndroid a = kind.type().create(level);
        if (a == null) {
            return;
        }
        a.moveTo(getX(), getY() + BED, getZ(), getYRot(), 0);
        a.setCharge(a.maxCharge());
        PortableAndroidPrinterItem.refill(printer, a, unit);
        PortableAndroidPrinterItem.equipPlate(printer, a, unit);
        PortableAndroidPrinterItem.equipWeapon(printer, a, unit);
        a.startPrinting();
        a.makeGuard(owner, blockPosition(), RuinAndroid.MODE_FOLLOW, 30);
        a.loseStation();
        level.addFreshEntity(a);
        android = a.getUUID();
        level.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    /** 印刷し終えたら、使った人の方へ1歩降りる（ふたを閉じる前に、印刷面を空ける）。 */
    private void stepOff(ServerLevel level) {
        if (android != null && level.getEntity(android) instanceof RuinAndroid a) {
            ServerPlayer p = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
            Vec3 to = p != null && p.level() == level ? p.position() : position().add(Vec3.directionFromRotation(0, getYRot()));
            Vec3 dir = to.subtract(position()).multiply(1, 0, 1);
            if (dir.lengthSqr() < 1e-4) {
                dir = Vec3.directionFromRotation(0, getYRot());
            }
            Vec3 step = position().add(dir.normalize().scale(1.2));
            a.getNavigation().moveTo(step.x, getY(), step.z, 1.0);
        }
    }

    /** 閉じたら、印刷機を使った人の持ち物へ戻す（いない・入らなければ、その場に落とす）。 */
    private void giveBack(ServerLevel level) {
        if (!printer.isEmpty()) {
            ServerPlayer p = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
            ItemStack stack = printer;
            printer = ItemStack.EMPTY;
            if (p == null || p.level() != level || p.distanceToSqr(this) > 64 * 64 || !p.getInventory().add(stack)) {
                level.addFreshEntity(new ItemEntity(level, getX(), getY() + 0.3, getZ(), stack));
            } else {
                level.playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.2F);
            }
        }
        discard();
    }

    // ------------------------------------------------------------------ ふるまい

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** コマンドなどで消されても、印刷機はなくさない（その場に落とす）。読み込みが外れただけなら、そのまま保存される。 */
    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && !printer.isEmpty() && level() instanceof ServerLevel level) {
            level.addFreshEntity(new ItemEntity(level, getX(), getY() + 0.3, getZ(), printer));
            printer = ItemStack.EMPTY;
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ 保存

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setPrinter(ItemStack.of(tag.getCompound("printer")));
        unit = tag.getInt("unit");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        android = tag.hasUUID("android") ? tag.getUUID("android") : null;
        kind = null;
        for (AndroidKind k : AndroidKind.values()) {
            if (k.id.equals(tag.getString("kind"))) {
                kind = k;
            }
        }
        entityData.set(AGE, tag.getInt("age"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!printer.isEmpty()) {
            tag.put("printer", printer.save(new CompoundTag()));
        }
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        if (android != null) {
            tag.putUUID("android", android);
        }
        if (kind != null) {
            tag.putString("kind", kind.id);
        }
        tag.putInt("age", age());
        tag.putInt("unit", unit);
    }
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }
}
