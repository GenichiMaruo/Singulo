package io.github.genichimaruo.singulo.compat.wthit;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineReport;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * WTHIT との連携（0.21.0。WTHIT が入っているときだけ、waila_plugins.json から読まれる）。Jade の連携と同じ報告
 * （{@link MachineReport}）を出す。蓄電の量は WTHIT が自分で出すので、ここでは出さない。
 * WTHIT はブロックエンティティのあるブロックにだけサーバーのデータを頼むので、マルチブロックの部品（ブロックエンティティがない）では、
 * コントローラーを見たときだけ出る。
 */
public final class SinguloWthitPlugin {
    static final ResourceLocation UID = Singulo.id("machine");
    static final String KEY = "singulo_report";

    private SinguloWthitPlugin() {}

    public static final class Common implements IWailaCommonPlugin {
        @Override
        public void register(ICommonRegistrar registrar) {
            registrar.featureConfig(UID, true);
            registrar.blockData(Data.INSTANCE, BlockEntity.class);
            // 小さなブラックホール・特異点・吸い込まれるブロックは現象なので、実体の名前を出さない
            registrar.blacklist(io.github.genichimaruo.singulo.registry.SinguloEntities.hiddenFromTooltips().toArray(net.minecraft.world.entity.EntityType<?>[]::new));
        }
    }

    public static final class Client implements IWailaClientPlugin {
        @Override
        public void register(IClientRegistrar registrar) {
            registrar.body(Body.INSTANCE, Block.class);
        }
    }

    enum Data implements IDataProvider<BlockEntity> {
        INSTANCE;

        @Override
        public void appendData(IDataWriter data, IServerAccessor<BlockEntity> accessor, IPluginConfig config) {
            if (!config.getBoolean(UID)) {
                return;
            }
            BlockEntity be = accessor.getTarget();
            if (!MachineReport.reports(be.getBlockState(), be)) {
                return;
            }
            MachineReport report = MachineReport.of(accessor.getLevel(), be.getBlockPos());
            if (report != null) {
                data.raw().put(KEY, report.save(accessor.getLevel().registryAccess()));
            }
        }
    }

    enum Body implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            if (!config.getBoolean(UID)) {
                return;
            }
            CompoundTag data = accessor.getData().raw();
            if (!data.contains(KEY)) {
                return;
            }
            MachineReport report = MachineReport.load(data.getCompound(KEY), accessor.getLevel().registryAccess());
            if (report != null) {
                for (Component line : report.lines(false)) {
                    tooltip.addLine(line);
                }
            }
        }
    }
}
