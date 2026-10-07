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
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /singulo build &lt;マルチブロック&gt; [大きさ] — クリエイティブ用。見ている向きの数ブロック先に、マルチブロックを一発で組み立てる。
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
                                        .executes(c -> build(c, IntegerArgumentType.getInteger(c, "size")))))));
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
