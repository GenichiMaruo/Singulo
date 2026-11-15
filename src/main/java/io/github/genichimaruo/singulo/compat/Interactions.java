package io.github.genichimaruo.singulo.compat;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
public final class Interactions {
 private Interactions() {}
 public static InteractionResult sidedSuccess(boolean client) { return client ? InteractionResult.SUCCESS : InteractionResult.CONSUME; }
 public static InteractionResult sidedSuccess(ItemStack stack, boolean client) { return (client ? InteractionResult.SUCCESS : InteractionResult.CONSUME).heldItemTransformedTo(stack); }
 public static InteractionResult success(ItemStack stack) { return InteractionResult.SUCCESS.heldItemTransformedTo(stack); }
 public static InteractionResult consume(ItemStack stack) { return InteractionResult.CONSUME.heldItemTransformedTo(stack); }
 public static InteractionResult fail(ItemStack stack) { return InteractionResult.FAIL; }
 public static InteractionResult pass(ItemStack stack) { return InteractionResult.PASS; }
}
