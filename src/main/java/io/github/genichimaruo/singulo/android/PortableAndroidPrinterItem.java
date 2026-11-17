package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.energy.EnergyText;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * 携帯型アンドロイド印刷機（0.21.0）。手で持てるカバン型の印刷機で、外出先でアンドロイドを1体ずつ印刷する。
 * <ul>
 * <li>組: 小型クリスタルメモリ（型を決める。減らない）・アンドロイドキット（印刷ごとに1つ）・自己修理キットのストック（1スタック）・
 *     アンドロイド用装甲板の4枠。Mk1 は1組、Mk2 は3組（{@link #units}）。中身はアイテムの中に持つ（{@link DataComponents#CONTAINER}）</li>
 * <li>印刷: 地面に置くたびに、そろっている次の組（Mk2 は組を順に回る。{@link SinguloComponents#PRINTER_NEXT}）で1体印刷する</li>
 * <li>アンドロイド用装甲板: 印刷したアンドロイドに積む（胸当てとして装備する）</li>
 * <li>自己修理キット: 印刷したアンドロイドに持たせ（アンドロイドの枠に入るだけ）、持ち歩いている間は、近くの自分の身寄りのないアンドロイドのキットが減っていれば補充する</li>
 * <li>電力: 組ごとに、入れたメモリの型の1体分（{@link #printFe}。メモリがなければ {@link #CAPACITY}）を蓄えられ、印刷ごとに
 *     組立ステーションの {@link #COST_FACTOR} 倍の電力（型の倍率つき）を使う。蓄電装置の充電の枠で充電する。メモリを替えて容量が
 *     減っても、溜まっている電力は消さない（使い切るまで、それ以上は充電しない）</li>
 * <li>使い方: 空中で右クリックすると画面を開く。印刷の準備ができていれば、地面を右クリック（または画面の印刷ボタン）で、
 *     カバンを地面に置いて開き（{@link PortablePrinterEntity}）、中の印刷面で印刷する。終わると閉じて持ち物へ戻る</li>
 * <li>印刷したアンドロイドは、使った人について歩く（追従モード）身寄りのないアンドロイドになる（組立ステーションでリンクし直せる）</li>
 * </ul>
 */
public class PortableAndroidPrinterItem extends Item {
    /** 1組あたりに蓄えられる電力の基準（倍率1の型の1体分。メモリのない組もこれだけ蓄えられる）。 */
    public static final int CAPACITY = 80_000;
    /** 1体の印刷に使う電力の、組立ステーションに対する倍率（持ち運べる分、高くつく）。 */
    public static final int COST_FACTOR = 2;
    /** 組の中の枠: メモリ・キット・自己修理キット・装甲板。 */
    public static final int SLOT_MEMORY = 0, SLOT_KIT = 1, SLOT_REPAIR = 2, SLOT_PLATE = 3, PER_UNIT = 4;
    /** 1組に積めるアンドロイドキットの数。 */
    public static final int MAX_KITS = 16;
    /** 自己修理キットを補充する、近くの身寄りのないアンドロイドまでの距離（ブロック）。 */
    static final double REFILL_RANGE = 16;
    /** 目の前のどこに置くか（足もとから水平にこのブロック数先）。 */
    static final int REACH = 2;

    private final int units;

    public PortableAndroidPrinterItem(Properties properties, int units) {
        super(properties.stacksTo(1));
        this.units = units;
    }

    /** 組の数（Mk1 は1、Mk2 は3）。印刷機でなければ0。 */
    public static int units(ItemStack stack) {
        return stack.getItem() instanceof PortableAndroidPrinterItem p ? p.units : 0;
    }

    public static int slots(ItemStack stack) {
        return units(stack) * PER_UNIT;
    }

    /** 組 unit の中の枠 slot の番号。 */
    public static int slot(int unit, int slot) {
        return unit * PER_UNIT + slot;
    }

    /** 蓄えられる電力: 組ごとの、入れたメモリの型の1体分の合計（メモリがなければ {@link #CAPACITY}）。 */
    public static int capacity(ItemStack stack) {
        int n = units(stack);
        if (n <= 0) {
            return CAPACITY;
        }
        int sum = 0;
        for (int u = 0; u < n; u++) {
            sum += printFe(stack, u);
        }
        return sum;
    }

    public static IEnergyStorage energy(ItemStack stack) {
        return new ComponentEnergyStorage(stack, SinguloComponents.ENERGY.get(), capacity(stack));
    }

    // ------------------------------------------------------------------ 中身

    public static NonNullList<ItemStack> contents(ItemStack stack) {
        NonNullList<ItemStack> out = NonNullList.withSize(Math.max(PER_UNIT, slots(stack)), ItemStack.EMPTY);
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(out);
        return out;
    }

    public static void setContents(ItemStack stack, List<ItemStack> items) {
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    @Nullable
    public static AndroidKind kind(ItemStack stack, int unit) {
        return AndroidStationBlockEntity.memoryKind(contents(stack).get(slot(unit, SLOT_MEMORY)));
    }

    /** 最初の組の型（Mk1 の型）。 */
    @Nullable
    public static AndroidKind kind(ItemStack stack) {
        return kind(stack, 0);
    }

    public static int kits(ItemStack stack, int unit) {
        ItemStack kit = contents(stack).get(slot(unit, SLOT_KIT));
        return AndroidStationBlockEntity.isKit(kit) ? kit.getCount() : 0;
    }

    public static int kits(ItemStack stack) {
        int n = 0;
        for (int u = 0; u < units(stack); u++) {
            n += kits(stack, u);
        }
        return n;
    }

    /** 組 unit の1体の印刷に使う電力（組立ステーションの {@link #COST_FACTOR} 倍。型の倍率つき）。メモリがなければ剣の型と同じ。 */
    public static int printFe(ItemStack stack, int unit) {
        AndroidKind kind = kind(stack, unit);
        return AndroidStationBlockEntity.PRINT_FE * COST_FACTOR * (kind == null ? 1 : kind.printCost);
    }

    /** 組 unit で印刷できない理由の翻訳キー（できるなら null）。 */
    @Nullable
    public static String problem(ItemStack stack, int unit) {
        if (kind(stack, unit) == null) {
            return "gui.singulo.portable_printer.no_memory";
        }
        if (kits(stack, unit) <= 0) {
            return "gui.singulo.portable_printer.no_kit";
        }
        if (energy(stack).getEnergyStored() < printFe(stack, unit)) {
            return "gui.singulo.portable_printer.no_energy";
        }
        return null;
    }

    /** 次に印刷する組（そろっている組を、前に印刷した組の次から順に探す）。なければ -1。 */
    public static int readyUnit(ItemStack stack) {
        int n = units(stack);
        int next = Math.floorMod(stack.getOrDefault(SinguloComponents.PRINTER_NEXT.get(), 0), Math.max(1, n));
        for (int i = 0; i < n; i++) {
            int u = (next + i) % n;
            if (problem(stack, u) == null) {
                return u;
            }
        }
        return -1;
    }

    /** 印刷できない理由（できるなら null）。メモリのある最初の組の理由、どの組にもメモリがなければ「メモリがない」。 */
    @Nullable
    public static String problem(ItemStack stack) {
        if (readyUnit(stack) >= 0) {
            return null;
        }
        for (int u = 0; u < units(stack); u++) {
            if (kind(stack, u) != null) {
                return problem(stack, u);
            }
        }
        return "gui.singulo.portable_printer.no_memory";
    }

    /** 自己修理キットのストックの合計。 */
    public static int repairKits(ItemStack stack) {
        List<ItemStack> items = contents(stack);
        int n = 0;
        for (int u = 0; u < units(stack); u++) {
            ItemStack s = items.get(slot(u, SLOT_REPAIR));
            if (RuinAndroid.isRepairKit(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /**
     * アンドロイドの自己修理キットの枠を、ストックから満たす（同じ種類のキットだけ重ねる）。unit が 0 以上ならその組から、
     * 負ならどの組からでも。動かした数を返す。ストックは書き戻す。
     */
    static int refill(ItemStack printer, RuinAndroid android, int unit) {
        List<ItemStack> items = new ArrayList<>(contents(printer));
        ItemStack carried = android.inventory().getItem(RuinAndroid.SLOT_REPAIR);
        int moved = 0;
        for (int u = 0; u < units(printer); u++) {
            if (unit >= 0 && u != unit) {
                continue;
            }
            ItemStack stock = items.get(slot(u, SLOT_REPAIR));
            if (!RuinAndroid.isRepairKit(stock)) {
                continue;
            }
            if (carried.isEmpty()) {
                carried = stock.split(stock.getMaxStackSize());
                moved += carried.getCount();
                android.inventory().setItem(RuinAndroid.SLOT_REPAIR, carried);
            } else if (ItemStack.isSameItemSameComponents(carried, stock) && carried.getCount() < carried.getMaxStackSize()) {
                int n = Math.min(stock.getCount(), carried.getMaxStackSize() - carried.getCount());
                stock.shrink(n);
                carried.grow(n);
                moved += n;
                android.inventory().setChanged();
            }
        }
        if (moved > 0) {
            setContents(printer, items);
        }
        return moved;
    }

    /** 組 unit の装甲板を、まだ積んでいないアンドロイドに積む（胸当てとして装備される）。積んだら true。印刷機の中身は書き戻す。 */
    static boolean equipPlate(ItemStack printer, RuinAndroid android, int unit) {
        List<ItemStack> items = new ArrayList<>(contents(printer));
        ItemStack plate = items.get(slot(unit, SLOT_PLATE));
        if (!RuinAndroid.isArmorPlate(plate) || !android.inventory().getItem(RuinAndroid.SLOT_PLATE).isEmpty()) {
            return false;
        }
        android.inventory().setItem(RuinAndroid.SLOT_PLATE, plate.copy());
        items.set(slot(unit, SLOT_PLATE), ItemStack.EMPTY);
        setContents(printer, items);
        return true;
    }

    /** 持ち歩いている間（1秒ごと）: 近くの自分の身寄りのないアンドロイドの自己修理キットを、ストックから補充する。 */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer player) || player.tickCount % 20 != 0
                || repairKits(stack) <= 0) {
            return;
        }
        for (RuinAndroid a : server.getEntitiesOfClass(RuinAndroid.class, player.getBoundingBox().inflate(REFILL_RANGE),
                a -> a.isAlive() && a.orphan() && player.getUUID().equals(a.owner()))) {
            refill(stack, a, -1);
            if (repairKits(stack) <= 0) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------ 使う

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            openMenu(sp, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** 地面の上面を右クリック: 準備ができていればそこに置いて印刷する。できなければ画面を開く。 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        if (context.getClickedFace() == Direction.UP && problem(stack) == null) {
            deploy(sp, context.getHand(), context.getClickedPos().above());
        } else {
            openMenu(sp, context.getHand());
        }
        return InteractionResult.CONSUME;
    }

    public static void openMenu(ServerPlayer player, InteractionHand hand) {
        Component title = player.getItemInHand(hand).getHoverName();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new PortablePrinterMenu(id, inv, hand), title), buf -> buf.writeEnum(hand));
    }

    /** 置ける所か: 足もとが固く、カバンの広さ（1×1）とアンドロイドの背丈（2段）が空いている。 */
    static boolean fits(ServerLevel level, BlockPos at) {
        if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
            return false;
        }
        AABB box = new AABB(at).expandTowards(0, 1, 0).deflate(0.05);
        return level.noCollision(box) && level.getEntitiesOfClass(Entity.class, box, e -> !e.isSpectator()).isEmpty();
    }

    /** 目の前（{@link #REACH} ブロック先）の、置ける地面。上下に2段まで探す。なければ null。 */
    @Nullable
    static BlockPos frontSpot(ServerPlayer player) {
        Direction facing = player.getDirection();
        List<BlockPos> tries = new ArrayList<>();
        for (int reach : new int[] {REACH, 1}) {
            BlockPos base = player.blockPosition().relative(facing, reach);
            for (int dy : new int[] {0, 1, -1, 2, -2}) {
                tries.add(base.above(dy));
            }
        }
        for (BlockPos p : tries) {
            if (fits(player.serverLevel(), p)) {
                return p;
            }
        }
        return null;
    }

    /**
     * 手に持った印刷機を at に置いて、次の組で印刷を始める。その組のキット1つと電力を使い、印刷機はカバン（{@link PortablePrinterEntity}）
     * として地面に置かれる（手から離れ、印刷が終わると持ち物へ戻る）。置けなければ理由を知らせて false。
     */
    public static boolean deploy(ServerPlayer player, InteractionHand hand, @Nullable BlockPos at) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof PortableAndroidPrinterItem)) {
            return false;
        }
        int unit = readyUnit(stack);
        if (unit < 0) {
            player.displayClientMessage(Component.translatable(problem(stack)), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (at == null || !fits(level, at)) {
            player.displayClientMessage(Component.translatable("gui.singulo.portable_printer.no_space"), true);
            return false;
        }
        AndroidKind kind = kind(stack, unit);
        energy(stack).extractEnergy(printFe(stack, unit), false);
        List<ItemStack> items = new ArrayList<>(contents(stack));
        items.get(slot(unit, SLOT_KIT)).shrink(1);
        setContents(stack, items);
        stack.set(SinguloComponents.PRINTER_NEXT.get(), (unit + 1) % units(stack));
        ItemStack carried = stack.copy();
        player.setItemInHand(hand, ItemStack.EMPTY);
        Vec3 c = Vec3.atBottomCenterOf(at);
        PortablePrinterEntity e = new PortablePrinterEntity(level, c, player.getYRot() + 180, carried, player.getUUID(), kind, unit);
        level.addFreshEntity(e);
        level.playSound(null, at, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
        return true;
    }

    // ------------------------------------------------------------------ 表示

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * energy(stack).getEnergyStored() / capacity(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x78E1FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int stored = energy(stack).getEnergyStored();
        tooltip.add(Component.translatable("tooltip.singulo.energy", EnergyText.of(stored), EnergyText.of(capacity(stack)))
                .withStyle(ChatFormatting.GRAY));
        for (int u = 0; u < units(stack); u++) {
            AndroidKind kind = kind(stack, u);
            tooltip.add(kind == null ? Component.translatable("gui.singulo.portable_printer.no_memory").withStyle(ChatFormatting.DARK_GRAY)
                    : Component.translatable("tooltip.singulo.portable_printer.kind", kind.type().getDescription(), kits(stack, u))
                    .withStyle(ChatFormatting.AQUA));
        }
        int repair = repairKits(stack);
        if (repair > 0) {
            tooltip.add(Component.translatable("tooltip.singulo.portable_printer.repair", repair).withStyle(ChatFormatting.GRAY));
        }
    }
}
