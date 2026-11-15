package io.github.genichimaruo.singulo.client;
import net.minecraft.world.entity.LivingEntity;
interface ExtractedLayerParent<T extends LivingEntity,M extends ExtractedEntityModel<T>> { M getModel(); }