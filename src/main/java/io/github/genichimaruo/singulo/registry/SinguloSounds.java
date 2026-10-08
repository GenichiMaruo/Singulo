package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 効果音。名前・ファイル・字幕・聞こえる距離は tools/gen_data.py の SOUNDS（assets/singulo/sounds.json を作る）と同じ。
 * "_running" で終わるものは装置が動いている間のくり返し（クライアントの MachineSounds が鳴らす）。
 */
public final class SinguloSounds {
    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(Registries.SOUND_EVENT, Singulo.MODID);
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> BY_NAME = new HashMap<>();

    static final String[] NAMES = {
            "black_hole.ambient", "black_hole.formation",
            "kiln_running", "compressor_running", "electrolyzer_running", "archive_terminal_running",
            "precision_assembler_running", "catalytic_reactor_running", "cooling_tower_running", "particle_accelerator_running",
            "entanglement_synthesizer_running", "laser_cooler_running", "echo_resonator_running",
            "degenerate_compactor_running", "casimir_cavity_running", "time_crystal_incubator_running",
            "singularity_encapsulator_running", "thermoelectric_generator_running", "cryogenic_turbine_running",
            "quantum_heat_engine_running", "smes_cell_running", "muon_collector_running", "worldline_anchor_running",
            "advanced_worldline_anchor_running", "advanced_worldline_anchor_embed", "inertial_stabilizer_running",
            "degenerate_furnace_running", "degenerate_furnace_press", "shield_tower_running", "tipler_cylinder_running",
            "wormhole_generator_charge", "wormhole_generator_open", "wormhole_mouth_ambient", "wormhole_stabilizer_running",
            "halo_collector_running", "containment_tank_running", "penrose_reactor_ignition", "probe_station_launch",
            "probe_station_return", "gravitational_wave_detector_ping", "multiblock_formed",
            "graviton_manipulator_active", "graviton_manipulator_charge", "graviton_manipulator_throw",
    };

    static {
        for (String name : NAMES) {
            BY_NAME.put(name, REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(Singulo.id(name))));
        }
    }

    /** 稼働中のブラックホールのうなり（くり返し）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACK_HOLE_AMBIENT = BY_NAME.get("black_hole.ambient");
    /** ブラックホールができる瞬間（リアクターの点火、ウォーデンの特異点）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACK_HOLE_FORMATION = BY_NAME.get("black_hole.formation");

    private SinguloSounds() {}

    /** 名前で引く（NAMES にないものは例外）。 */
    public static SoundEvent get(String name) {
        DeferredHolder<SoundEvent, SoundEvent> h = BY_NAME.get(name);
        if (h == null) {
            throw new IllegalArgumentException("効果音がない: " + name);
        }
        return h.get();
    }

    /** ブロックの位置で、周りの全員に聞こえるように鳴らす（サーバー）。 */
    public static void playAt(Level level, BlockPos pos, String name, float volume, float pitch) {
        level.playSound(null, pos, get(name), SoundSource.BLOCKS, volume, pitch);
    }
}
