package io.github.genichimaruo.singulo.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * /singulo build &lt;マルチブロック&gt; [大きさ] — クリエイティブ用。見ている向きの数ブロック先に、マルチブロックを一発で組み立てる。
 * /singulo blackhole [位置] [質量] — 野良ブラックホールを出す（消すには /setblock で空気にする）。
 * /singulo ruin &lt;遺構&gt; [位置] — 遺構をその場に建てる（床の中心が位置。省くと足もと）。保管庫や警備機も入る。
 * コントローラが手前、形が奥に伸びる（ホロ投影機と同じ向き）。権限レベル2（オペレーター）が要る。
 */
public final class SinguloCommands {
    private SinguloCommands() {}

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("singulo")
                .then(Commands.literal("build").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("multiblock", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Blueprints.Kind.values()).map(Blueprints.Kind::id), b))
                                .executes(c -> build(c, -1))
                                .then(Commands.argument("size", IntegerArgumentType.integer(1, 64))
                                        .executes(c -> build(c, IntegerArgumentType.getInteger(c, "size"))))))
                .then(Commands.literal("blackhole").requires(s -> s.hasPermission(2))
                        .executes(c -> blackHole(c, null, DEFAULT_BLACK_HOLE_MASS))
                        .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(c -> blackHole(c, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(c, "pos"),
                                        DEFAULT_BLACK_HOLE_MASS))
                                .then(Commands.argument("mass", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(100, 20000))
                                        .executes(c -> blackHole(c, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(c, "pos"),
                                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "mass"))))))
                .then(Commands.literal("ruin").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("ruin", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(RUINS, b))
                                .executes(c -> ruin(c, null))
                                .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .executes(c -> ruin(c, net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                .getLoadedBlockPos(c, "pos")))))));
    }

    /** 建てられる遺構。 */
    static final java.util.List<String> RUINS = java.util.List.of(
            io.github.genichimaruo.singulo.item.ExplorerCompassItem.TARGETS);

    /** /singulo ruin &lt;遺構&gt; [位置] */
    private static int ruin(CommandContext<CommandSourceStack> c, BlockPos pos) throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, "ruin");
        if (!RUINS.contains(id)) {
            c.getSource().sendFailure(Component.translatable("command.singulo.ruin.unknown", id));
            return 0;
        }
        if (pos == null) {
            pos = BlockPos.containing(c.getSource().getPosition());
        }
        net.minecraft.core.Vec3i size = placeRuin(c.getSource().getLevel(), id, pos);
        if (size == null) {
            c.getSource().sendFailure(Component.translatable("command.singulo.ruin.unknown", id));
            return 0;
        }
        BlockPos at = pos;
        c.getSource().sendSuccess(() -> Component.translatable("command.singulo.ruin.done",
                Component.translatable("ruin.singulo." + id), at.getX(), at.getY(), at.getZ()), true);
        return 1;
    }

    /** 遺構の構造物を、床の中心が center に来るように置く。置いた大きさを返す（構造物がなければ null）。 */
    @javax.annotation.Nullable
    public static net.minecraft.core.Vec3i placeRuin(ServerLevel level, String id, BlockPos center) {
        var template = level.getStructureManager().get(io.github.genichimaruo.singulo.Singulo.id("ruins/" + id));
        if (template.isEmpty()) {
            return null;
        }
        net.minecraft.core.Vec3i size = template.get().getSize();
        BlockPos origin = center.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        template.get().placeInWorld(level, origin, origin,
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), level.getRandom(),
                Block.UPDATE_CLIENTS);
        return size;
    }

    private static int build(CommandContext<CommandSourceStack> c, int size) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "multiblock");
        Blueprints.Kind kind = Blueprints.Kind.byId(id);
        if (kind == null) {
            c.getSource().sendFailure(Component.translatable("command.singulo.build.unknown", id));
            return 0;
        }
        if (size < 0) {
            size = kind.defaultSize();
        }
        if (!kind.validSize(size)) {
            c.getSource().sendFailure(Component.translatable("command.singulo.build.size", size));
            return 0;
        }
        Direction back = player.getDirection();
        BlockPos controller = player.blockPosition().relative(back, frontGap(kind));
        int placed = place((ServerLevel) player.level(), kind, controller, back, size);
        int finalSize = size;
        c.getSource().sendSuccess(() -> Component.translatable("command.singulo.build.done",
                Component.translatable("multiblock.singulo." + kind.id()), finalSize, placed), true);
        return placed;
    }

    static final double DEFAULT_BLACK_HOLE_MASS = 2000;

    /** /singulo blackhole [位置] [質量] — 野良ブラックホールを出す。位置を省くと、見ている方向の8ブロック先。 */
    private static int blackHole(CommandContext<CommandSourceStack> c, BlockPos pos, double mass) throws CommandSyntaxException {
        ServerLevel level = c.getSource().getLevel();
        if (pos == null) {
            ServerPlayer player = c.getSource().getPlayerOrException();
            pos = BlockPos.containing(player.getEyePosition().add(player.getLookAngle().scale(8)));
        }
        level.setBlock(pos, io.github.genichimaruo.singulo.registry.SinguloBlocks.ROGUE_BLACK_HOLE.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof io.github.genichimaruo.singulo.reactor.RogueBlackHoleBlockEntity hole) {
            hole.setCore(mass, 0);
        }
        BlockPos at = pos;
        c.getSource().sendSuccess(() -> Component.translatable("command.singulo.blackhole.done", at.getX(), at.getY(), at.getZ(),
                String.format("%.0f", mass)), true);
        return 1;
    }

    /** プレイヤーとコントローラの間の距離（形が手前に張り出す分だけ離す）。 */
    static int frontGap(Blueprints.Kind kind) {
        return switch (kind) {
            case PENROSE_REACTOR -> 8;
            case SHIELD_TOWER, TIPLER_CYLINDER, WORMHOLE_GENERATOR -> 3;
            default -> 2;
        };
    }

    /** 設計図どおりにブロックを置く（コントローラは最後）。置いた数を返す。 */
    public static int place(ServerLevel level, Blueprints.Kind kind, BlockPos controller, Direction back, int size) {
        Map<BlockPos, BlockState> layout = Blueprints.layout(kind, controller, back, size);
        int n = 0;
        BlockState controllerState = layout.get(controller);
        for (Map.Entry<BlockPos, BlockState> e : layout.entrySet()) {
            if (!e.getKey().equals(controller)) {
                level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_ALL);
                n++;
            }
        }
        level.setBlock(controller, controllerState, Block.UPDATE_ALL);
        return n + 1;
    }
}
