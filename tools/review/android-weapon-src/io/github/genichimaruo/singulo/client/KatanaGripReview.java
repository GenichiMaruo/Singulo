package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.genichimaruo.singulo.android.KatanaAndroid;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** At handoff the wrist must match both the grip point and the complete blade frame at the scabbard. */
final class KatanaGripReview {
    private static final class Subject extends KatanaAndroid {
        private final boolean backpack;

        Subject(ClientLevel level, boolean backpack, int state) {
            super(SinguloEntities.KATANA_ANDROID.get(), level);
            this.backpack = backpack;
            setState(state);
        }

        @Override public boolean hasBackpack() { return backpack; }

        void enter(int state) { setState(state); }
        void confirmIaiReturn() { setIaiReturning(true); }
    }

    static void verify(Minecraft mc) {
        verifySteelMesh();
        AndroidModel model = new AndroidModel(mc.getEntityModels().bakeLayer(AndroidModel.KATANA_LAYER));
        for (boolean backpack : new boolean[]{false, true}) {
            check(model, mc.level, backpack, false, KatanaAndroid.STATE_DRAW, 3.5F, KatanaAndroid.DRAW_CLEAR);
            check(model, mc.level, backpack, false, KatanaAndroid.STATE_SHEATHE, KatanaAndroid.SHEATHE_SEAT, 3);
            check(model, mc.level, backpack, true, KatanaAndroid.STATE_IAI_FLASH, 0.5F, 1);
            check(model, mc.level, backpack, true, KatanaAndroid.STATE_IAI_ZANSHIN,
                    KatanaAndroid.IAI_SHEATHE_SEAT, KatanaAndroid.IAI_SHEATHE_GUIDE);
            checkVictory(model, mc.level, backpack);
            checkIaiVictory(model, mc.level, backpack);
            checkLateIaiDecision(model, mc.level, backpack);
            checkMaintenance(model, mc.level, backpack);
        }
    }

    private record CrossPoint(float x, float z) {
        static CrossPoint midpoint(GlassStairsWater.V a, GlassStairsWater.V b) {
            return new CrossPoint((a.x() + b.x()) * 0.5F, (a.z() + b.z()) * 0.5F);
        }
    }

    /** Slice the emitted quads halfway along each segment, including the clipped segment at the scabbard mouth. */
    static void verifySteelMesh() {
        for (float exposed : new float[]{0.043F, 0.39F, KatanaMotion.stroke(false)}) {
            var capture = new GlassStairsWater.Capture();
            KatanaBlade.render(new PoseStack().last(), type -> { throw new AssertionError("Unexpected steel overlay"); },
                    capture, 0, false, exposed, 0, 0, -1);
            var vertices = capture.done();
            Map<Float, Map<CrossPoint, CrossPoint>> rings = new HashMap<>();
            Set<Integer> colors = new HashSet<>();
            float end = 0;
            for (int i = 0; i < vertices.size(); i += 4) {
                var a = vertices.get(i);
                var b = vertices.get(i + 1);
                end = Math.max(end, b.y());
                colors.add(a.r() << 16 | a.g() << 8 | a.b());
                CrossPoint from = CrossPoint.midpoint(a, b);
                CrossPoint to = CrossPoint.midpoint(vertices.get(i + 2), vertices.get(i + 3));
                var ring = rings.computeIfAbsent((a.y() + b.y()) * 0.5F, y -> new HashMap<>());
                if (ring.put(from, to) != null
                        || from.x > 0 && to.x > 0 && from.z >= to.z
                        || from.x < 0 && to.x < 0 && from.z <= to.z) {
                    throw new AssertionError("Cross-section doubles back or has duplicate steel at " + from);
                }
            }
            if (colors.size() != 3 || Math.abs(end - exposed) > 1e-6F) {
                throw new AssertionError("Expected three steel bands up to " + exposed + ", got " + colors + " up to " + end);
            }
            for (var ring : rings.values()) {
                CrossPoint start = ring.keySet().iterator().next();
                CrossPoint point = start;
                int wraps = 0;
                while (ring.containsKey(point)) {
                    CrossPoint next = ring.remove(point);
                    if (Math.signum(point.x) != Math.signum(next.x)) wraps++;
                    point = next;
                }
                if (!point.equals(start) || !ring.isEmpty() || wraps != 2) {
                    throw new AssertionError("Blade bands must form one closed perimeter with only a spine and a cutting edge");
                }
            }
        }
    }

    private static void check(AndroidModel model, ClientLevel level, boolean backpack, boolean crimson,
                              int state, float guided, float handoff) {
        Subject subject = new Subject(level, backpack, state);
        if (state == KatanaAndroid.STATE_IAI_ZANSHIN) subject.confirmIaiReturn();
        for (float time : new float[]{guided, handoff - 0.001F, handoff, handoff + 0.001F}) {
            subject.tickCount = (int) time;
            model.setupAnim(subject, 0, 0, time, 0, 0);
            float exposed = KatanaMotion.exposure(state, time, crimson, false);
            checkGrip(model, subject, crimson, time, exposed);
        }
    }

    private static void checkGrip(AndroidModel model, Subject subject, boolean crimson, float time, float exposed) {
        PoseStack mouth = new PoseStack(), hand = new PoseStack();
        model.toKatanaMouth(mouth, crimson);
        model.toKatanaHand(hand, crimson);
        Matrix4f wrist = new Matrix4f(mouth.last().pose()).invert().mul(hand.last().pose());
        float gripY = -AndroidModel.KATANA_GRIP_TO_GUARD - KatanaMotion.stroke(crimson) * exposed;
        float gap = wrist.getTranslation(new Vector3f()).mul(AndroidRenderer.SCALE)
                .distance(0, gripY, KatanaMotion.curveOffset(exposed));
        // +Y points down the blade; +Z points towards its cutting edge (also detects a rolled wrist).
        float axis = wrist.transformDirection(new Vector3f(0, 1, 0)).distance(0, 1, 0);
        float edge = wrist.transformDirection(new Vector3f(0, 0, 1)).distance(0, 0, 1);
        if (!(gap < 0.001F && axis < 0.002F && edge < 0.002F)) {
            throw new AssertionError("Katana grip misaligned at state=" + subject.state() + " t=" + time + " backpack=" + subject.hasBackpack()
                    + " gap(blocks)=" + gap + " blade axis error=" + axis + " edge direction error=" + edge);
        }
    }

    private static float victoryExposure(Subject subject, float partialTick) {
        float clock = subject.victoryTime(partialTick);
        return clock < 0 ? 0 : KatanaMotion.exposure(KatanaAndroid.STATE_VICTORY, clock, true, false);
    }

    private static void checkVictory(AndroidModel model, ClientLevel level, boolean backpack) {
        Subject subject = new Subject(level, backpack, KatanaAndroid.STATE_VICTORY);
        subject.drawForTest();
        float[] samples = {KatanaAndroid.VICTORY_GRIP, (KatanaAndroid.VICTORY_GRIP + KatanaAndroid.VICTORY_RAISE) / 2F,
                KatanaAndroid.VICTORY_RAISE, KatanaAndroid.VICTORY_RETURN,
                (KatanaAndroid.VICTORY_RETURN + KatanaAndroid.VICTORY_SEAT) / 2F, KatanaAndroid.VICTORY_SEAT};
        float[] expected = {0, 0.26F, 0.52F, 0.52F, 0.26F, 0};
        for (int i = 0; i < samples.length; i++) {
            float t = samples[i];
            subject.tickCount = (int) t;
            model.setupAnim(subject, 0, 0, t, 0, 0);
            float exposed = victoryExposure(subject, t - subject.tickCount);
            if (Math.abs(exposed - expected[i]) > 0.001F) throw new AssertionError("Victory blade exposure at " + t + ": " + exposed);
            checkGrip(model, subject, true, t, exposed);
        }
        for (int interrupt : new int[]{9, 18, 27}) {
            subject = new Subject(level, backpack, KatanaAndroid.STATE_VICTORY);
            subject.drawForTest();
            subject.tickCount = interrupt;
            model.setupAnim(subject, 0, 0, interrupt, 0, 0);
            float previous = victoryExposure(subject, 0);
            Vector3f before = victoryGuard(model, previous);
            subject.enter(KatanaAndroid.STATE_IDLE);
            for (int half = 0; half <= 8; half++) {
                float elapsed = half / 2F;
                subject.tickCount = interrupt + half / 2;
                if (half == 2) subject.enter(KatanaAndroid.STATE_CUT);
                model.setupAnim(subject, 0, 0, interrupt + elapsed, 0, 0);
                float pt = elapsed - (int) elapsed;
                float exposed = victoryExposure(subject, pt);
                if (half == 0 && (Math.abs(previous - exposed) > 0.001F || before.distance(victoryGuard(model, exposed)) > 0.001F)) {
                    throw new AssertionError("Victory sword jumped on interruption at " + interrupt);
                }
                if (exposed > previous + 0.0001F || half == 8 && (exposed != 0 || subject.victoryTime(pt) >= 0)) {
                    throw new AssertionError("Victory sword failed to reseat after interruption at " + interrupt + " + " + elapsed);
                }
                if (exposed > 0) checkGrip(model, subject, true, interrupt + elapsed, exposed);
                previous = exposed;
            }
        }
    }

    private static Vector3f victoryGuard(AndroidModel model, float exposed) {
        PoseStack mouth = new PoseStack();
        model.toKatanaMouth(mouth, true);
        return mouth.last().pose().transformPosition(new Vector3f(0, -KatanaMotion.stroke(true) * exposed,
                KatanaMotion.curveOffset(exposed)).div(AndroidRenderer.SCALE)).mul(AndroidRenderer.SCALE);
    }

    /** Match the renderer's choice of blade coordinates. The tip reveals orientation jumps that the guard alone misses. */
    private static Vector3f crimsonTip(AndroidModel model, int state, float t, float exposed) {
        PoseStack blade = new PoseStack();
        float shift;
        if (KatanaMotion.handBlend(state, t, true, false) > 0) {
            model.toKatanaHand(blade, true);
            shift = AndroidModel.KATANA_GRIP_TO_GUARD;
        } else {
            model.toKatanaMouth(blade, true);
            shift = -KatanaMotion.stroke(true) * exposed;
            blade.translate(0, 0, KatanaMotion.curveOffset(exposed) / AndroidRenderer.SCALE);
        }
        Vector3f tip = new Vector3f(0, shift + KatanaMotion.stroke(true) * exposed, -KatanaMotion.curveOffset(exposed));
        return blade.last().pose().transformPosition(tip.div(AndroidRenderer.SCALE)).mul(AndroidRenderer.SCALE);
    }

    private static void checkIaiVictory(AndroidModel model, ClientLevel level, boolean backpack) {
        int zanshin = KatanaAndroid.STATE_IAI_ZANSHIN, victory = KatanaAndroid.STATE_IAI_VICTORY;
        Subject subject = new Subject(level, backpack, zanshin);
        subject.drawForTest();
        for (int half = 0; half <= 2 * KatanaAndroid.IAI_RELEASE; half++) {
            float t = half / 2F;
            subject.tickCount = (int) t;
            model.setupAnim(subject, 0, 0, t, 0, 0);
            requireFullBlade(zanshin, t);
        }
        Vector3f lastTip = crimsonTip(model, zanshin, KatanaAndroid.IAI_RELEASE, 1);
        subject.enter(victory);
        float previous = 1;
        for (int half = 0; half <= 2 * KatanaAndroid.IAI_VICTORY; half++) {
            float t = half / 2F, age = KatanaAndroid.IAI_RELEASE + t;
            subject.tickCount = (int) age;
            model.setupAnim(subject, 0, 0, age, 0, 0);
            float clock = subject.iaiVictoryTime(age - subject.tickCount);
            if (Math.abs(clock - t) > 0.001F) throw new AssertionError("Incorrect finisher victory clock at " + t);
            float exposed = KatanaMotion.exposure(victory, t, true, true);
            if (t <= KatanaAndroid.IAI_VICTORY_RETURN) requireFullBlade(victory, t);
            if (half == 0 && lastTip.distance(crimsonTip(model, victory, t, exposed)) > 0.001F) {
                throw new AssertionError("紅一閃 blade tip jumped as the victory began");
            }
            if (t == KatanaAndroid.IAI_VICTORY_RETURN / 2F && lastTip.distance(crimsonTip(model, victory, t, exposed)) < 0.3F) {
                throw new AssertionError("The drawn blade did not move into its victory pose");
            }
            checkInsertion(model, subject, t, previous, exposed);
            previous = exposed;
        }
        for (int interrupt : new int[]{8, KatanaAndroid.IAI_VICTORY_RETURN + 2, KatanaAndroid.IAI_VICTORY_GUIDE + 3}) {
            subject = new Subject(level, backpack, victory);
            subject.drawForTest();
            subject.tickCount = interrupt;
            model.setupAnim(subject, 0, 0, interrupt, 0, 0);
            previous = KatanaMotion.exposure(victory, interrupt, true, true);
            Vector3f tip = crimsonTip(model, victory, interrupt, previous);
            subject.enter(KatanaAndroid.STATE_IDLE);
            for (int half = 0; half <= 16; half++) {
                float elapsed = half / 2F;
                subject.tickCount = interrupt + (int) elapsed;
                if (half == 2) subject.enter(KatanaAndroid.STATE_CUT);
                model.setupAnim(subject, 0, 0, interrupt + elapsed, 0, 0);
                float clock = subject.iaiVictoryTime(elapsed - (int) elapsed);
                float exposed = clock < 0 ? 0 : KatanaMotion.exposure(victory, clock, true, true);
                if (half == 0) {
                    float jump = tip.distance(crimsonTip(model, victory, clock, exposed));
                    if (Math.abs(clock - interrupt) > 0.001F || jump > 0.001F) {
                        throw new AssertionError("Finisher blade jumped " + jump + " blocks on interruption at " + interrupt + " (clock=" + clock + ")");
                    }
                }
                if (half == 16 ? clock >= 0 || previous != 0 : clock < 0) {
                    throw new AssertionError("Interrupted finisher did not finish sheathing in 8 ticks");
                }
                if (clock >= 0) checkInsertion(model, subject, clock, previous, exposed);
                previous = exposed;
            }
        }
    }

    private static void requireFullBlade(int state, float time) {
        if (KatanaMotion.exposure(state, time, true, false) != 1 || KatanaMotion.handBlend(state, time, true, false) != 1) {
            throw new AssertionError("Expected a fully drawn blade in the left hand at state=" + state + " t=" + time);
        }
    }

    /** A late server packet must never show an early sheathing stroke followed by a fresh draw. */
    private static void checkLateIaiDecision(AndroidModel model, ClientLevel level, boolean backpack) {
        int release = KatanaAndroid.IAI_RELEASE, late = release + 12;
        for (boolean kill : new boolean[]{false, true}) {
            Subject subject = new Subject(level, backpack, KatanaAndroid.STATE_IAI_ZANSHIN);
            subject.drawForTest();
            subject.tickCount = release;
            model.setupAnim(subject, 0, 0, release, 0, 0);
            Matrix4f held = crimsonHandAtTorso(model);
            for (int half = 0; half <= 24; half++) {
                float age = release + half / 2F;
                subject.tickCount = (int) age;
                float clock = subject.stateTime(age - subject.tickCount);
                if (clock != Math.nextDown((float) release)) throw new AssertionError("Zanshin advanced while awaiting the kill result at " + age);
                requireFullBlade(subject.state(), clock);
                model.setupAnim(subject, 0, 0, age, 0, 0);
                if (!held.equals(crimsonHandAtTorso(model), 0.001F)) {
                    throw new AssertionError("Unsheathed blade drifted while awaiting the kill result at " + age);
                }
            }
            Vector3f beforePacket = crimsonTip(model, subject.state(), release, 1);
            if (kill) subject.enter(KatanaAndroid.STATE_IAI_VICTORY);
            else subject.confirmIaiReturn();
            float clock = subject.stateTime(0);
            model.setupAnim(subject, 0, 0, late, 0, 0);
            if (clock != (kill ? 0 : release) || beforePacket.distance(crimsonTip(model, subject.state(), clock, 1)) > 0.001F) {
                throw new AssertionError("Late decision moved the blade on receipt, kill=" + kill + " clock=" + clock);
            }
            subject.tickCount++;
            if (subject.stateTime(0.5F) != clock + 1.5F) throw new AssertionError("Animation failed to resume after confirmation");
        }
    }

    private static Matrix4f crimsonHandAtTorso(AndroidModel model) {
        PoseStack torso = new PoseStack(), hand = new PoseStack();
        model.toTorso(torso);
        model.toKatanaHand(hand, true);
        return new Matrix4f(torso.last().pose()).invert().mul(hand.last().pose());
    }

    private static void checkMaintenance(AndroidModel model, ClientLevel level, boolean backpack) {
        int state = KatanaAndroid.STATE_MAINTENANCE;
        Subject subject = new Subject(level, backpack, state);
        for (int tick = 0; tick <= KatanaAndroid.MAINTENANCE; tick++) {
            subject.tickCount = tick;
            model.setupAnim(subject, 0, 0, tick, 0, 0);
            boolean held = tick >= KatanaAndroid.MAINTENANCE_CLOTH && tick < KatanaAndroid.MAINTENANCE_STOW;
            if (KatanaMotion.clothInHand(state, tick) != held || KatanaMotion.exposure(state, tick, true, false) != 0) {
                throw new AssertionError("Wrong cloth or crimson blade during maintenance at " + tick);
            }
            if (tick == KatanaAndroid.MAINTENANCE_CLOTH || tick == KatanaAndroid.MAINTENANCE_STOW) {
                Matrix4f atBox = new Matrix4f().translation(KatanaCareKit.clothX(backpack) / 16, KatanaCareKit.CLOTH_Y / 16,
                        KatanaCareKit.clothZ(backpack) / 16);
                if (!atBox.equals(crimsonHandAtTorso(model), 0.003F)) {
                    throw new AssertionError("Cloth jumped between the box and the hand at " + tick + ", backpack=" + backpack);
                }
            }
            if (tick >= KatanaAndroid.MAINTENANCE_DRAW && tick <= KatanaAndroid.MAINTENANCE_CLEAR
                    || tick >= KatanaAndroid.MAINTENANCE_GUIDE && tick <= KatanaAndroid.MAINTENANCE_SEAT) {
                checkGrip(model, subject, false, tick, KatanaMotion.exposure(state, tick, false, false));
            }
            if (tick == KatanaAndroid.MAINTENANCE_WIPE || tick == 47 || tick == KatanaAndroid.MAINTENANCE_RETURN) {
                PoseStack sword = new PoseStack(), cloth = new PoseStack();
                model.toKatanaHand(sword, false);
                model.toKatanaHand(cloth, true);
                Vector3f contact = new Matrix4f(sword.last().pose()).invert().mul(cloth.last().pose())
                        .getTranslation(new Vector3f()).mul(AndroidRenderer.SCALE);
                float along = KatanaMotion.wipePosition(tick);
                float y = AndroidModel.KATANA_GRIP_TO_GUARD + KatanaMotion.stroke(false) * along;
                if (!subject.bladeInHand(0) || contact.distance(0, y, -KatanaMotion.curveOffset(along)) > 0.08F) {
                    throw new AssertionError("Cloth missed the steel at " + tick + ", contact=" + contact + ", backpack=" + backpack);
                }
            }
        }
    }

    private static void checkInsertion(AndroidModel model, Subject subject, float clock, float previous, float exposed) {
        if (exposed > previous || clock <= KatanaAndroid.IAI_VICTORY_GUIDE && exposed != 1
                || clock > KatanaAndroid.IAI_VICTORY_GUIDE && clock < KatanaAndroid.IAI_VICTORY_SEAT && !(exposed > 0 && exposed < 1)
                || clock >= KatanaAndroid.IAI_VICTORY_SEAT && exposed != 0) {
            throw new AssertionError("Expected a single uninterrupted sheathing stroke at " + clock + ", exposed=" + exposed);
        }
        if (clock >= KatanaAndroid.IAI_VICTORY_GUIDE && clock <= KatanaAndroid.IAI_VICTORY_SEAT) {
            checkGrip(model, subject, true, clock, exposed);
        }
    }
}
