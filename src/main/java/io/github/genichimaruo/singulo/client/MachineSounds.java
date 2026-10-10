package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.GravityGauntletItem;
import io.github.genichimaruo.singulo.item.GravitonManipulatorItem;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

/**
 * 装置の動作音（クライアント）。近くで動いている（光っている）装置ごとに、その装置の音をくり返し鳴らす。
 * 止まる・壊れる・離れると消える。たくさん並んでもうるさくなりすぎないよう、近い MAX_LOOPS 台だけ鳴らす。
 * ワームホール生成器のためる音は、動き始めたときに1回だけ鳴らす（10秒）。
 * グラビトン・マニピュレーターの操作中とため中の音もここで鳴らす（自分のプレイヤーの手元から）。
 */
final class MachineSounds {
    /** 装置ごとの音・大きさ・くり返すか。キーはブロックの ID。 */
    private record Spec(String sound, float volume, boolean loop) {}

    private static final Map<String, Spec> BY_BLOCK = new HashMap<>();
    private static final int MAX_LOOPS = 12;
    private static final double RANGE = 24;
    private static final Map<BlockPos, SoundInstance> PLAYING = new HashMap<>();
    @Nullable
    private static HeldLoop active;
    @Nullable
    private static HeldLoop charge;

    static {
        put(0.7F, "kiln", "kiln_running");
        put(0.7F, "compressor", "compressor_running");
        put(0.7F, "electrolyzer", "electrolyzer_running");
        put(0.6F, "archive_terminal", "archive_terminal_running");
        put(0.7F, "precision_assembler", "precision_assembler_running");
        put(0.7F, "catalytic_reactor", "catalytic_reactor_running");
        put(1.0F, "cooling_tower_controller", "cooling_tower_running");
        put(1.0F, "accelerator_controller", "particle_accelerator_running");
        put(0.7F, "entanglement_synthesizer", "entanglement_synthesizer_running");
        put(0.7F, "laser_cooler", "laser_cooler_running");
        put(0.7F, "echo_resonator", "echo_resonator_running");
        put(1.0F, "degenerate_compactor_controller", "degenerate_compactor_running");
        put(1.0F, "casimir_cavity_controller", "casimir_cavity_running");
        put(0.7F, "time_crystal_incubator", "time_crystal_incubator_running");
        put(0.8F, "singularity_encapsulator", "singularity_encapsulator_running");
        put(0.5F, "thermoelectric_generator", "thermoelectric_generator_running");
        put(0.8F, "cryogenic_turbine", "cryogenic_turbine_running");
        put(0.7F, "quantum_heat_engine", "quantum_heat_engine_running");
        put(0.5F, "smes_cell", "smes_cell_running");
        put(0.5F, "cosmic_muon_collector", "muon_collector_running");
        put(0.6F, "worldline_anchor_small", "worldline_anchor_running");
        put(0.8F, "worldline_anchor_advanced", "advanced_worldline_anchor_running");
        put(0.7F, "inertial_stabilizer", "inertial_stabilizer_running");
        put(1.0F, "degenerate_furnace_controller", "degenerate_furnace_running");
        put(1.0F, "shield_tower_core", "shield_tower_running");
        put(1.0F, "tipler_core", "tipler_cylinder_running");
        put(0.8F, "wormhole_stabilizer", "wormhole_stabilizer_running");
        put(0.7F, "halo_collector", "halo_collector_running");
        put(0.5F, "gravitational_containment_tank", "containment_tank_running");
        put(0.8F, "wormhole_mouth", "wormhole_mouth_ambient");
        BY_BLOCK.put("wormhole_generator_core", new Spec("wormhole_generator_charge", 1.2F, false));
    }

    private MachineSounds() {}

    private static void put(float volume, String block, String sound) {
        BY_BLOCK.put(block, new Spec(sound, volume, true));
    }

    @Nullable
    private static Spec specOf(BlockState state) {
        if (!state.hasProperty(AbstractMachineBlock.LIT) || !state.getValue(AbstractMachineBlock.LIT)) {
            return null;
        }
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return Singulo.MODID.equals(id.getNamespace()) ? BY_BLOCK.get(id.getPath()) : null;
    }

    static void onClientTick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null) {
            PLAYING.clear();
            active = null;
            charge = null;
            return;
        }
        tickHeld(mc, player);
        if (player.tickCount % 10 != 0) {
            return;
        }
        // 動いていない・離れた装置の音を止める（1回だけの音は、止まるまで鳴らし直さないよう覚えておく）
        PLAYING.entrySet().removeIf(e -> {
            BlockState state = mc.level.getBlockState(e.getKey());
            boolean keep = specOf(state) != null && e.getKey().distToCenterSqr(player.position()) <= (RANGE + 4) * (RANGE + 4);
            if (!keep) {
                mc.getSoundManager().stop(e.getValue());
                return true;
            }
            return false;
        });
        // 近くで動いている装置を、近い順に MAX_LOOPS 台まで鳴らす
        List<BlockPos> lit = new ArrayList<>();
        int r = (int) Math.ceil(RANGE / 16);
        int pcx = player.blockPosition().getX() >> 4;
        int pcz = player.blockPosition().getZ() >> 4;
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (pos.distToCenterSqr(player.position()) <= RANGE * RANGE && specOf(be.getBlockState()) != null) {
                        lit.add(pos);
                    }
                }
            }
        }
        lit.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(player.position())));
        for (int i = 0; i < Math.min(MAX_LOOPS, lit.size()); i++) {
            BlockPos pos = lit.get(i);
            SoundInstance playing = PLAYING.get(pos);
            Spec spec = specOf(mc.level.getBlockState(pos));
            if (spec == null) {
                continue;
            }
            if (playing == null || (spec.loop() && !mc.getSoundManager().isActive(playing))) {
                MachineLoop sound = new MachineLoop(pos, spec);
                PLAYING.put(pos, sound);
                mc.getSoundManager().play(sound);
            }
        }
    }

    /** 装置の位置から鳴る音。装置が止まる・壊れる・別のブロックになると消える。 */
    private static final class MachineLoop extends AbstractTickableSoundInstance {
        private final BlockPos pos;
        private final Block block;

        MachineLoop(BlockPos pos, Spec spec) {
            super(SinguloSounds.get(spec.sound()), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.pos = pos;
            this.block = Minecraft.getInstance().level.getBlockState(pos).getBlock();
            this.looping = spec.loop();
            this.delay = 0;
            this.volume = spec.volume();
            this.attenuation = Attenuation.LINEAR;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;
        }

        @Override
        public void tick() {
            var level = Minecraft.getInstance().level;
            BlockState state = level == null ? null : level.getBlockState(pos);
            if (state == null || state.getBlock() != block || specOf(state) == null) {
                stop();
            }
        }
    }

    // ------------------------------------------------------------------ グラビトン・マニピュレーター

    /** 左クリックで操っている間の音と、右クリックでためている間の音（ためるほど高くなる）。 */
    private static void tickHeld(Minecraft mc, LocalPlayer player) {
        boolean holding = mc.screen == null && player.getMainHandItem().getItem() instanceof GravitonManipulatorItem;
        boolean cooldown = holding && player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem());
        boolean operating = holding && !cooldown && mc.options.keyAttack.isDown();
        boolean charging = holding && !cooldown && mc.options.keyUse.isDown() && !player.isShiftKeyDown()
                && player.getMainHandItem().getItem() instanceof GravitonManipulatorItem m
                && m.mode(player.getMainHandItem()) == GravityGauntletItem.Mode.LEVITATE;
        active = keep(mc, active, operating, "graviton_manipulator_active", 0.7F);
        charge = keep(mc, charge, charging, "graviton_manipulator_charge", 0.8F);
    }

    @Nullable
    private static HeldLoop keep(Minecraft mc, @Nullable HeldLoop loop, boolean on, String sound, float volume) {
        if (on && (loop == null || !mc.getSoundManager().isActive(loop))) {
            loop = new HeldLoop(sound, volume);
            mc.getSoundManager().play(loop);
        } else if (!on && loop != null) {
            loop.end();
            loop = null;
        }
        return loop;
    }

    /** 自分のプレイヤーの手元から鳴る音。ため音は時間とともに高くなる（2秒でいちばん高い）。 */
    private static final class HeldLoop extends AbstractTickableSoundInstance {
        private final boolean rising;
        private int age;

        HeldLoop(String sound, float volume) {
            super(SinguloSounds.get(sound), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.rising = sound.endsWith("charge");
            this.looping = true;
            this.delay = 0;
            this.volume = volume;
            this.pitch = rising ? 0.8F : 1.0F;
            follow();
        }

        private void follow() {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) {
                this.x = p.getX();
                this.y = p.getEyeY();
                this.z = p.getZ();
            }
        }

        void end() {
            stop();
        }

        @Override
        public void tick() {
            if (Minecraft.getInstance().player == null) {
                stop();
                return;
            }
            follow();
            age++;
            if (rising) {
                this.pitch = 0.8F + 0.8F * Math.min(1, age / (float) GravitonManipulatorItem.MAX_CHARGE);
            }
        }
    }
}
