package io.github.genichimaruo.singulo.compat.jade;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineReport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade との連携（0.21.0。Jade が入っているときだけ、Jade がこのクラスを見つけて読む）。見ている装置の様子
 * （{@link MachineReport}: 状態と止まっている理由・進み具合・使う/作る電力・触媒の残り・マルチブロックの足りない部品）を出す。
 * 蓄電の量は Jade が自分で出すので、ここでは出さない。
 */
@WailaPlugin
public final class SinguloJadePlugin implements IWailaPlugin {
    static final ResourceLocation UID = Singulo.id("machine");
    static final String KEY = "singulo_report";

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(Server.INSTANCE, Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Client.INSTANCE, Block.class);
        // ブラックホール爆弾の小さなブラックホールは現象なので、実体の名前を出さない
        registration.hideTarget(io.github.genichimaruo.singulo.registry.SinguloEntities.MICRO_BLACK_HOLE.get());
    }

    /** サーバー: 報告を作って送る。装置かマルチブロックの部品のときだけ頼まれる。 */
    enum Server implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            MachineReport report = MachineReport.of(accessor.getLevel(), accessor.getPosition());
            if (report != null) {
                data.put(KEY, report.save(accessor.getLevel().registryAccess()));
            }
        }

        @Override
        public boolean shouldRequestData(BlockAccessor accessor) {
            return MachineReport.reports(accessor.getBlockState(), accessor.getBlockEntity());
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    /** クライアント: 届いた報告を行にして出す。 */
    enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(KEY)) {
                return;
            }
            MachineReport report = MachineReport.load(data.getCompound(KEY), accessor.getLevel().registryAccess());
            if (report != null) {
                for (Component line : report.lines(false)) {
                    tooltip.add(line);
                }
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
