package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.Singulo;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * クライアント → サーバー: 作業指令台の画面の操作（計算・注文・取り消し・つながりを外す）。中身は NBT。
 * 開いている画面の指令台にしか効かず、操作できる人（許可証）でなければ何もしない。
 */
public record WorkConsoleActionPayload(int containerId, CompoundTag action) {

    public static final String PLAN = "plan", START = "start", CANCEL = "cancel", UNLINK = "unlink", GRID = "grid", PROCESS = "process",
            REGISTER = "register", UNREGISTER = "unregister";


    public static CompoundTag plan(ItemStack item, long count, net.minecraft.core.HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        t.putString("type", PLAN);
        t.put("item", item.copyWithCount(1).save(new net.minecraft.nbt.CompoundTag()));
        t.putLong("count", count);
        return t;
    }

    public static CompoundTag start(ItemStack item, long count, List<UUID> androids, BlockPos destination,
                                    net.minecraft.core.HolderLookup.Provider reg) {
        CompoundTag t = plan(item, count, reg);
        t.putString("type", START);
        t.putLong("destination", destination.asLong());
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (UUID id : androids) {
            list.add(NbtUtils.createUUID(id));
        }
        t.put("androids", list);
        return t;
    }

    /** 注文を登録する（trigger は {@link StandingOrder#KEEP} など）。 */
    public static CompoundTag register(ItemStack item, long count, int trigger, List<UUID> androids, BlockPos destination,
                                       net.minecraft.core.HolderLookup.Provider reg) {
        CompoundTag t = start(item, count, androids, destination, reg);
        t.putString("type", REGISTER);
        t.putInt("trigger", trigger);
        return t;
    }

    public static CompoundTag unregister(int id) {
        CompoundTag t = new CompoundTag();
        t.putString("type", UNREGISTER);
        t.putInt("id", id);
        return t;
    }

    public static CompoundTag cancel(int id) {
        CompoundTag t = new CompoundTag();
        t.putString("type", CANCEL);
        t.putInt("id", id);
        return t;
    }

    /** 記録の見本を置き換える（JEI の自動配置）。 */
    public static CompoundTag grid(List<ItemStack> grid, net.minecraft.core.HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        t.putString("type", GRID);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (ItemStack s : grid) {
            list.add(s.isEmpty() ? new CompoundTag() : s.copyWithCount(1).save(new net.minecraft.nbt.CompoundTag()));
        }
        t.put("grid", list);
        return t;
    }

    /** 加工モードの見本を置き換える（JEI の自動配置。入れる物は数つき）。 */
    public static CompoundTag process(List<ItemStack> inputs, List<ItemStack> outputs, net.minecraft.core.HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        t.putString("type", PROCESS);
        t.put("inputs", items(inputs, reg));
        t.put("outputs", items(outputs, reg));
        return t;
    }

    private static net.minecraft.nbt.ListTag items(List<ItemStack> items, net.minecraft.core.HolderLookup.Provider reg) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                list.add(s.save(new net.minecraft.nbt.CompoundTag()));
            }
        }
        return list;
    }

    private static List<ItemStack> items(net.minecraft.nbt.ListTag list, net.minecraft.core.HolderLookup.Provider reg) {
        List<ItemStack> out = new ArrayList<>();
        for (Tag t : list) {
            ItemStack s = ItemStack.of((CompoundTag) t);
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    public static CompoundTag unlink(BlockPos pos) {
        CompoundTag t = new CompoundTag();
        t.putString("type", UNLINK);
        t.putLong("pos", pos.asLong());
        return t;
    }

    public static void encode(WorkConsoleActionPayload p, net.minecraft.network.FriendlyByteBuf b) { b.writeVarInt(p.containerId()); b.writeNbt(p.action()); }
    public static WorkConsoleActionPayload decode(net.minecraft.network.FriendlyByteBuf b) { return new WorkConsoleActionPayload(b.readVarInt(), java.util.Objects.requireNonNull(b.readNbt())); }

    public static void handle(WorkConsoleActionPayload payload, net.minecraftforge.network.NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof WorkConsoleMenu menu)
                    || menu.containerId != payload.containerId() || !menu.stillValid(player)
                    || !(player.level().getBlockEntity(menu.pos()) instanceof WorkConsoleBlockEntity console) || !console.mayOperate(player)) {
                return;
            }
            CompoundTag a = payload.action();
            var reg = player.level().registryAccess();
            ItemStack item = ItemStack.of(a.getCompound("item"));
            long count = Math.max(1, Math.min(WorkConsoleBlockEntity.MAX_COUNT, a.getLong("count")));
            CompoundTag plan = null;
            switch (a.getString("type")) {
                case PLAN -> {
                    if (!item.isEmpty()) {
                        plan = WorkConsoleView.plan(console, console.plan(item, count), reg);
                    }
                }
                case START -> {
                    List<UUID> ids = new ArrayList<>();
                    for (Tag t : a.getList("androids", Tag.TAG_INT_ARRAY)) {
                        ids.add(NbtUtils.loadUUID(t));
                    }
                    BlockPos destination = a.contains("destination") ? BlockPos.of(a.getLong("destination")) : null;
                    Component error = item.isEmpty() ? Component.literal("?") : console.start(player, item, count, ids, destination);
                    plan = new CompoundTag();
                    plan.putBoolean("started", error == null);
                    if (error != null) {
                        plan.putString("error", Component.Serializer.toJson(error));
                    }
                }
                case REGISTER -> {
                    List<UUID> ids = new ArrayList<>();
                    for (Tag t : a.getList("androids", Tag.TAG_INT_ARRAY)) {
                        ids.add(NbtUtils.loadUUID(t));
                    }
                    BlockPos destination = a.contains("destination") ? BlockPos.of(a.getLong("destination")) : null;
                    Component error = item.isEmpty() ? Component.literal("?")
                            : console.register(player, item, count, a.getInt("trigger"), destination, ids);
                    plan = new CompoundTag();
                    plan.putBoolean("started", error == null);
                    if (error != null) {
                        plan.putString("error", Component.Serializer.toJson(error));
                    }
                }
                case UNREGISTER -> {
                    if (console.mayOperate(player)) {
                        console.unregister(a.getInt("id"));
                    }
                }
                case CANCEL -> console.cancel(a.getInt("id"), null);
                case UNLINK -> console.unlink(BlockPos.of(a.getLong("pos")));
                case GRID -> {
                    List<ItemStack> grid = new ArrayList<>();
                    for (Tag t : a.getList("grid", Tag.TAG_COMPOUND)) {
                        grid.add(((CompoundTag) t).isEmpty() ? ItemStack.EMPTY : ItemStack.of((CompoundTag) t));
                    }
                    menu.setGrid(player, grid);
                    return;
                }
                case PROCESS -> {
                    menu.setProcess(player, items(a.getList("inputs", Tag.TAG_COMPOUND), reg), items(a.getList("outputs", Tag.TAG_COMPOUND), reg));
                    return;
                }
                default -> {
                    return;
                }
            }
            console.sendView(player, menu.containerId, plan);
        });
    }
}
