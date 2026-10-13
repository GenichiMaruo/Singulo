package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.ruin.HorizonBolt;
import io.github.genichimaruo.singulo.ruin.HorizonWarden;
import io.github.genichimaruo.singulo.ruin.SecurityDrone;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SinguloEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(Registries.ENTITY_TYPE, Singulo.MODID);

    public static final Supplier<EntityType<SecurityDrone>> SECURITY_DRONE = REGISTER.register("security_drone",
            () -> EntityType.Builder.of(SecurityDrone::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.6F).clientTrackingRange(8).build(Singulo.id("security_drone").toString()));

    public static final Supplier<EntityType<HorizonWarden>> HORIZON_WARDEN = REGISTER.register("horizon_warden",
            () -> EntityType.Builder.of(HorizonWarden::new, MobCategory.MONSTER)
                    .sized(1.4F, 3.2F).clientTrackingRange(10).fireImmune().build(Singulo.id("horizon_warden").toString()));

    public static final Supplier<EntityType<HorizonBolt>> HORIZON_BOLT = REGISTER.register("horizon_bolt",
            () -> EntityType.Builder.<HorizonBolt>of(HorizonBolt::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(6).updateInterval(2).build(Singulo.id("horizon_bolt").toString()));

    /** 投げたブラックホール爆弾。 */
    public static final Supplier<EntityType<io.github.genichimaruo.singulo.reactor.BlackHoleBomb>> BLACK_HOLE_BOMB = REGISTER.register(
            "black_hole_bomb", () -> EntityType.Builder.<io.github.genichimaruo.singulo.reactor.BlackHoleBomb>of(
                    io.github.genichimaruo.singulo.reactor.BlackHoleBomb::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(6).updateInterval(2).build(Singulo.id("black_hole_bomb").toString()));
    /** ブラックホール爆弾が開いた小さなブラックホール（保存しない）。 */
    public static final Supplier<EntityType<io.github.genichimaruo.singulo.reactor.MicroBlackHole>> MICRO_BLACK_HOLE = REGISTER.register(
            "micro_black_hole_entity", () -> EntityType.Builder.<io.github.genichimaruo.singulo.reactor.MicroBlackHole>of(
                    io.github.genichimaruo.singulo.reactor.MicroBlackHole::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(20).noSave().fireImmune()
                    .build(Singulo.id("micro_black_hole_entity").toString()));

    /** ホライズン・ウォーデンの小型の特異点（保存しない）。 */
    public static final Supplier<EntityType<io.github.genichimaruo.singulo.ruin.WardenSingularity>> WARDEN_SINGULARITY = REGISTER.register(
            "warden_singularity", () -> EntityType.Builder.<io.github.genichimaruo.singulo.ruin.WardenSingularity>of(
                    io.github.genichimaruo.singulo.ruin.WardenSingularity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(8).updateInterval(10).noSave().fireImmune()
                    .build(Singulo.id("warden_singularity").toString()));

    /** 研究棟のボス部屋の番人。 */
    public static final Supplier<EntityType<io.github.genichimaruo.singulo.ruin.EchoSentinel>> ECHO_SENTINEL = REGISTER.register(
            "echo_sentinel", () -> EntityType.Builder.of(io.github.genichimaruo.singulo.ruin.EchoSentinel::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.4F).clientTrackingRange(10).fireImmune().build(Singulo.id("echo_sentinel").toString()));
    /** 封鎖培養施設のボス部屋の番人。 */
    public static final Supplier<EntityType<io.github.genichimaruo.singulo.ruin.GravityRemnant>> GRAVITY_REMNANT = REGISTER.register(
            "gravity_remnant", () -> EntityType.Builder.of(io.github.genichimaruo.singulo.ruin.GravityRemnant::new, MobCategory.MONSTER)
                    .sized(1.8F, 1.8F).clientTrackingRange(10).fireImmune().build(Singulo.id("gravity_remnant").toString()));
    /** 重力の澱が撃ち出す瓦礫。 */
    public static final Supplier<EntityType<io.github.genichimaruo.singulo.ruin.GravityDebris>> GRAVITY_DEBRIS = REGISTER.register(
            "gravity_debris", () -> EntityType.Builder.<io.github.genichimaruo.singulo.ruin.GravityDebris>of(
                    io.github.genichimaruo.singulo.ruin.GravityDebris::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).clientTrackingRange(6).updateInterval(2).build(Singulo.id("gravity_debris").toString()));

    /** 沈んだ炉心区画のボス（体は部位 LeviathanSegment に分かれる）。 */
    public static final Supplier<EntityType<io.github.genichimaruo.singulo.ruin.ErgosphereLeviathan>> ERGOSPHERE_LEVIATHAN = REGISTER.register(
            "ergosphere_leviathan", () -> EntityType.Builder.of(io.github.genichimaruo.singulo.ruin.ErgosphereLeviathan::new, MobCategory.MONSTER)
                    .sized(2.2F, 2.2F).clientTrackingRange(12).updateInterval(1).fireImmune()
                    .build(Singulo.id("ergosphere_leviathan").toString()));
    /** 隠しボス（最終実験施設の封印コンソールで、終焉の記録を使うと目覚める）。 */
    public static final Supplier<EntityType<io.github.genichimaruo.singulo.ruin.LastArchivist>> LAST_ARCHIVIST = REGISTER.register(
            "last_archivist", () -> EntityType.Builder.of(io.github.genichimaruo.singulo.ruin.LastArchivist::new, MobCategory.MONSTER)
                    .sized(1.2F, 3.4F).clientTrackingRange(12).fireImmune().build(Singulo.id("last_archivist").toString()));

    /** 光刃型アンドロイド「ルミナ」（プレイヤーより少し小さい）。 */
    public static final Supplier<EntityType<io.github.genichimaruo.singulo.android.BladeAndroid>> BLADE_ANDROID = REGISTER.register(
            "blade_android", () -> EntityType.Builder.of(io.github.genichimaruo.singulo.android.BladeAndroid::new, MobCategory.MONSTER)
                    .sized(0.55F, 1.65F).eyeHeight(1.46F).clientTrackingRange(10).build(Singulo.id("blade_android").toString()));
    /** 鞭型アンドロイド「ヴェルナ」。 */
    public static final Supplier<EntityType<io.github.genichimaruo.singulo.android.WhipAndroid>> WHIP_ANDROID = REGISTER.register(
            "whip_android", () -> EntityType.Builder.of(io.github.genichimaruo.singulo.android.WhipAndroid::new, MobCategory.MONSTER)
                    .sized(0.55F, 1.65F).eyeHeight(1.46F).clientTrackingRange(10).build(Singulo.id("whip_android").toString()));
    /** 光剣型アンドロイド「ソレイユ」。 */
    public static final Supplier<EntityType<io.github.genichimaruo.singulo.android.SaberAndroid>> SABER_ANDROID = REGISTER.register(
            "saber_android", () -> EntityType.Builder.of(io.github.genichimaruo.singulo.android.SaberAndroid::new, MobCategory.MONSTER)
                    .sized(0.55F, 1.65F).eyeHeight(1.46F).clientTrackingRange(10).build(Singulo.id("saber_android").toString()));
    /** 光剣型の斬撃波。 */
    public static final Supplier<EntityType<io.github.genichimaruo.singulo.android.AndroidCrescent>> ANDROID_CRESCENT = REGISTER.register(
            "android_crescent", () -> EntityType.Builder.<io.github.genichimaruo.singulo.android.AndroidCrescent>of(
                    io.github.genichimaruo.singulo.android.AndroidCrescent::new, MobCategory.MISC)
                    .sized(0.8F, 0.4F).clientTrackingRange(8).updateInterval(1).build(Singulo.id("android_crescent").toString()));
    /** アンドロイドが床から立てる光の柱。 */
    public static final Supplier<EntityType<io.github.genichimaruo.singulo.android.AndroidLaser>> ANDROID_LASER = REGISTER.register(
            "android_laser", () -> EntityType.Builder.<io.github.genichimaruo.singulo.android.AndroidLaser>of(
                    io.github.genichimaruo.singulo.android.AndroidLaser::new, MobCategory.MISC)
                    .sized(1.8F, 4.0F).clientTrackingRange(8).updateInterval(10).build(Singulo.id("android_laser").toString()));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(BLADE_ANDROID.get(), io.github.genichimaruo.singulo.android.RuinAndroid.createAttributes().build());
        event.put(WHIP_ANDROID.get(), io.github.genichimaruo.singulo.android.RuinAndroid.createAttributes().build());
        event.put(SABER_ANDROID.get(), io.github.genichimaruo.singulo.android.RuinAndroid.createAttributes().build());
        event.put(SECURITY_DRONE.get(), SecurityDrone.createAttributes().build());
        event.put(HORIZON_WARDEN.get(), HorizonWarden.createAttributes().build());
        event.put(ECHO_SENTINEL.get(), io.github.genichimaruo.singulo.ruin.EchoSentinel.createAttributes().build());
        event.put(GRAVITY_REMNANT.get(), io.github.genichimaruo.singulo.ruin.GravityRemnant.createAttributes().build());
        event.put(ERGOSPHERE_LEVIATHAN.get(), io.github.genichimaruo.singulo.ruin.ErgosphereLeviathan.createAttributes().build());
        event.put(LAST_ARCHIVIST.get(), io.github.genichimaruo.singulo.ruin.LastArchivist.createAttributes().build());
    }

    private SinguloEntities() {}
}
