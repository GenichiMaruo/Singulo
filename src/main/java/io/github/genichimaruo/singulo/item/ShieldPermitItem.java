package io.github.genichimaruo.singulo.item;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * シールド許可証。イベントホライズン・シールド発生塔の許可証スロットに入れると、守りの中で設置・破壊・物の取り出しができるのは
 * 登録された人だけになる（放射冠だけは誰でも壊せる）。
 * <ul>
 * <li>右クリック: 自分を登録する。</li>
 * <li>ほかのプレイヤーに右クリック: その人と自分を登録する（登録した人は必ず入る。締め出されないように）。</li>
 * <li>スニークして右クリック: 登録を自分だけにする。</li>
 * </ul>
 */
public class ShieldPermitItem extends SinguloItem {
    private static final String KEY = "permit_members";
    private static final int SHOWN = 8;

    public record Member(UUID id, String name) {}

    public ShieldPermitItem(Properties properties, int stage) {
        super(properties.stacksTo(1), stage, false);
    }

    /** 登録された人。 */
    public static List<Member> members(ItemStack stack) {
        List<Member> out = new ArrayList<>();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return out;
        }
        ListTag list = data.copyTag().getList(KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (t.hasUUID("id")) {
                out.add(new Member(t.getUUID("id"), t.getString("name")));
            }
        }
        return out;
    }

    /** この人が登録されているか。 */
    public static boolean allows(ItemStack stack, Player player) {
        UUID id = player.getUUID();
        for (Member m : members(stack)) {
            if (m.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static void write(ItemStack stack, List<Member> members) {
        ListTag list = new ListTag();
        for (Member m : members) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", m.id());
            t.putString("name", m.name());
            list.add(t);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(KEY, list));
    }

    /** 登録する（もう入っていれば名前だけ新しくする）。新しく入ったら true。 */
    public static boolean add(ItemStack stack, Player player) {
        List<Member> members = members(stack);
        String name = player.getGameProfile().getName();
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).id().equals(player.getUUID())) {
                members.set(i, new Member(player.getUUID(), name));
                write(stack, members);
                return false;
            }
        }
        members.add(new Member(player.getUUID(), name));
        write(stack, members);
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (player.isSecondaryUseActive()) {
                write(stack, List.of(new Member(player.getUUID(), player.getGameProfile().getName())));
                player.displayClientMessage(Component.translatable("item.singulo.shield_permit.reset", player.getName()), true);
            } else {
                boolean added = add(stack, player);
                player.displayClientMessage(Component.translatable(added ? "item.singulo.shield_permit.added"
                        : "item.singulo.shield_permit.already", player.getName(), members(stack).size()), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player other)) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide) {
            // 手に持っている許可証そのものを書き換える（引数の stack は写しのことがある）
            ItemStack held = player.getItemInHand(hand);
            add(held, other);
            add(held, player);
            player.displayClientMessage(Component.translatable("item.singulo.shield_permit.added", other.getName(),
                    members(held).size()), true);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        List<Member> members = members(stack);
        if (members.isEmpty()) {
            tooltip.add(Component.translatable("item.singulo.shield_permit.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.singulo.shield_permit.members", members.size()).withStyle(ChatFormatting.AQUA));
            for (int i = 0; i < Math.min(SHOWN, members.size()); i++) {
                tooltip.add(Component.literal(" ・" + members.get(i).name()).withStyle(ChatFormatting.WHITE));
            }
            if (members.size() > SHOWN) {
                tooltip.add(Component.translatable("item.singulo.shield_permit.more", members.size() - SHOWN).withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip.add(Component.translatable("item.singulo.shield_permit.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
