package io.github.genichimaruo.singulo.mixin;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.compat.PocketAttachmentMigration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Preserve the old raw-list attachment before NeoForge requests its compound child. */
@Mixin(value = AttachmentHolder.class, remap = false)
public abstract class PocketAttachmentMigrationMixin {
    @ModifyVariable(method = "deserializeAttachments", at = @At("HEAD"), argsOnly = true)
    private ValueInput singulo$migratePocket(ValueInput input) {
        CompoundTag original = input.read(MapCodec.assumeMapUnsafe(CompoundTag.CODEC)).orElseThrow(
                () -> new IllegalStateException("Cannot inspect attachments for pocket migration"));
        CompoundTag migrated = PocketAttachmentMigration.migrate(original);
        if (migrated == original) return input;
        return TagValueInput.create(new MigrationProblems("attachments"), input.lookup(), migrated);
    }
    private record MigrationProblems(String path) implements ProblemReporter {
        @Override public ProblemReporter forChild(PathElement child) { return new MigrationProblems(path + child.get()); }
        @Override public void report(Problem problem) {
            throw new IllegalStateException("Pocket migration decode failed at " + path + ": " + problem.description());
        }
    }
}
