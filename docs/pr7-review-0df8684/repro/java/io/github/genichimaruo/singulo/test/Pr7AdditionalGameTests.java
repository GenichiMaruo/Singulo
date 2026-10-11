package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.data.ConfigMigration;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.UsesData;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.WorldlineAnchorBlockEntity;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.recipe.RecipeTweaks;
import io.github.genichimaruo.singulo.registry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("singulo_review")
@PrefixGameTestTemplate(false)
public final class Pr7AdditionalGameTests {
    private static ItemStack stack(String id, int n) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)), n);
    }
    private static MachineRecipe recipe(GameTestHelper h, String path) {
        RecipeTweaks.invalidate();
        var r=h.getLevel().getRecipeManager().getAllRecipesFor(SinguloRecipes.MACHINE.get()).stream()
                .filter(x -> x.id().equals(Singulo.id(path))).findFirst().orElseThrow();
        return RecipeTweaks.adjust(r.id(),r.value());
    }
    @GameTest(template="empty",batch="pr7_lifetime_matrix")
    public static void lifetimeMatrix(GameTestHelper h) {
        String oldPreset=ServerConfig.PRESET.get();
        int oldLife=ServerConfig.TIME_CRYSTAL_LIFETIME_MINUTES.get();
        double oldMultiplier=ServerConfig.LIFETIME_MULTIPLIER.get();
        try {
            for (String p:List.of("standard","custom","casual","expert")) {
                ServerConfig.PRESET.set(p);
                for(int life:new int[]{1,20,45,1440}) {
                    ServerConfig.TIME_CRYSTAL_LIFETIME_MINUTES.set(life);
                    for(double mult:new double[]{0.1,1,100}) {
                        ServerConfig.LIFETIME_MULTIPLIER.set(mult);
                        ItemStack fresh=stack("time_crystal_catalyst",1);
                        int max=UsesHelper.baseMax(fresh);
                        MachineRecipe r=recipe(h,"precision_assembler/time_crystal_module");
                        int cost=r.ingredients().stream().filter(x->x.uses()>0).findFirst().orElseThrow().uses();
                        h.assertTrue(Math.abs(cost-max*2.0/3)<=1,"module ratio: "+p+"/"+life+"/"+mult);
                        h.assertTrue(r.plan(List.of(fresh,stack("optical_lattice_substrate",1),stack("steel_plate",2)))!=null,"fresh cannot craft");
                        ItemStack weak=fresh.copy();
                        weak.set(SinguloComponents.USES.get(),new UsesData(max/2,max,0));
                        h.assertTrue(r.plan(List.of(weak,stack("optical_lattice_substrate",1),stack("steel_plate",2)))==null,"weak can craft");
                    }
                }
            }
        } finally {
            ServerConfig.PRESET.set(oldPreset); ServerConfig.TIME_CRYSTAL_LIFETIME_MINUTES.set(oldLife);
            ServerConfig.LIFETIME_MULTIPLIER.set(oldMultiplier); RecipeTweaks.invalidate();
        }
        h.succeed();
    }
    private static int nitrogen(MachineRecipe r) {
        return r.fluidIngredients().stream().mapToInt(MachineRecipe.FluidInput::amount).sum();
    }
    @GameTest(template="empty",batch="pr7_core_leftover")
    public static void moduleLeftoverCanServeFirstCore(GameTestHelper h) {
        MachineRecipe r=recipe(h,"singularity_encapsulator/singularity_core");
        List<ItemStack> inputs=new ArrayList<>();
        for(var in:r.ingredients()) {
            ItemStack s=in.ingredient().getItems()[0].copyWithCount(in.count());
            if(s.is(stack("time_crystal_catalyst",1).getItem())) {
                s.set(SinguloComponents.USES.get(),new UsesData(36000,54000,0));
                h.assertTrue(in.uses()==0 && in.count()==1,"core life threshold unexpectedly present");
            }
            inputs.add(s);
        }
        h.assertTrue(r.plan(inputs)!=null,"module leftover cannot serve the first core");
        h.succeed();
    }
    @GameTest(template="empty",batch="pr7_coolant_matrix")
    public static void coolantMatrix(GameTestHelper h) {
        String oldPreset=ServerConfig.PRESET.get(); int oldCoolant=ServerConfig.SHELL_COOLANT_MB.get();
        try {
            int[] quantities={0,1,4000,6000,8000};
            int[][] normal={{0,1,4000,6000,8000},{0,1,2000,3000,4000},{0,2,6000,8000,8000}};
            int[][] strange={{0,1,2000,3000,4000},{0,1,1000,1500,2000},{0,2,3000,4500,6000}};
            String[] presets={"standard","casual","expert"};
            for(int p=0;p<3;p++) {
                ServerConfig.PRESET.set(presets[p]);
                for(int q=0;q<quantities.length;q++) {
                    ServerConfig.SHELL_COOLANT_MB.set(quantities[q]);
                    h.assertTrue(nitrogen(recipe(h,"degenerate_compactor/degenerate_matter_shell"))==normal[p][q],"normal coolant "+presets[p]+"/"+quantities[q]);
                    h.assertTrue(nitrogen(recipe(h,"degenerate_compactor/degenerate_matter_shell_from_strange_matter"))==strange[p][q],"strange coolant "+presets[p]+"/"+quantities[q]);
                }
            }
        } finally {ServerConfig.PRESET.set(oldPreset);ServerConfig.SHELL_COOLANT_MB.set(oldCoolant);RecipeTweaks.invalidate();}
        h.succeed();
    }
    @GameTest(template="empty",batch="pr7_migration_guard")
    @SuppressWarnings("unchecked")
    public static void versionOneKeepsLegacyValues(GameTestHelper h) {
        int a=ServerConfig.DEGENERATE_FURNACE_OUTPUT.get(),b=ServerConfig.SHELL_METAL_CORES.get(),c=ServerConfig.REACTOR_PLATES_PER_SHELL.get();
        try {
            h.assertTrue(ServerConfig.SETTINGS_VERSION.get()==1,"migration marker not persisted");
            ServerConfig.DEGENERATE_FURNACE_OUTPUT.set(20000000);ServerConfig.SHELL_METAL_CORES.set(2);ServerConfig.REACTOR_PLATES_PER_SHELL.set(12);
            var field=ConfigTracker.class.getDeclaredField("configsByMod");field.setAccessible(true);
            var configs=(Map<String,List<ModConfig>>)field.get(ConfigTracker.INSTANCE);
            ModConfig cfg=configs.get(Singulo.MODID).stream().filter(x->x.getSpec()==ServerConfig.SPEC).findFirst().orElseThrow();
            ConfigMigration.onConfig(new ModConfigEvent.Reloading(cfg));
            h.assertTrue(ServerConfig.DEGENERATE_FURNACE_OUTPUT.get()==20000000 && ServerConfig.SHELL_METAL_CORES.get()==2 && ServerConfig.REACTOR_PLATES_PER_SHELL.get()==12,"version 1 custom values overwritten");
        } catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
        finally {ServerConfig.DEGENERATE_FURNACE_OUTPUT.set(a);ServerConfig.SHELL_METAL_CORES.set(b);ServerConfig.REACTOR_PLATES_PER_SHELL.set(c);RecipeTweaks.invalidate();}
        h.succeed();
    }
    // Exercises NBT and onLoad registration in reverse order; it is not a full process restart.
    @GameTest(template="empty",batch="pr7_anchor_reload",timeoutTicks=45)
    public static void anchorOrderSurvivesNbtReload(GameTestHelper h) {
        int old=ServerConfig.MAX_ANCHORS_PER_PLAYER.get();ServerConfig.MAX_ANCHORS_PER_PLAYER.set(3);
        UUID owner=UUID.randomUUID(); List<WorldlineAnchorBlockEntity> anchors=new ArrayList<>();
        for(int i=0;i<3;i++) {
            h.setBlock(new BlockPos(1+2*i,1,2),SinguloBlocks.WORLDLINE_ANCHOR_SMALL.get());
            WorldlineAnchorBlockEntity a=h.getBlockEntity(new BlockPos(1+2*i,1,2));a.setOwner(owner);anchors.add(a);
        }
        h.onEachTick(()->anchors.forEach(a->a.energy().setEnergy(a.energy().getMaxEnergyStored())));
        for(int i=0;i<3;i++) {final int j=i;h.runAtTickTime(2+2*i,()->anchors.get(j).catalystSlot().insertItem(0,stack("muon_catalyst",1),false));}
        h.runAtTickTime(12,()-> {
            h.assertTrue(WorldlineAnchorBlockEntity.activeCount(owner)==3,"fixture not started");
            List<CompoundTag> tags=anchors.stream().map(a->a.saveWithFullMetadata(h.getLevel().registryAccess())).toList();
            anchors.forEach(WorldlineAnchorBlockEntity::setRemoved);
            ServerConfig.MAX_ANCHORS_PER_PLAYER.set(1);
            for(int i=2;i>=0;i--) {
                var before=anchors.get(i);
                var after=new WorldlineAnchorBlockEntity(before.getBlockPos(),before.getBlockState());
                after.loadWithComponents(tags.get(i),h.getLevel().registryAccess());
                h.getLevel().setBlockEntity(after);after.onLoad();anchors.set(i,after);
                h.assertTrue(after.saveWithFullMetadata(h.getLevel().registryAccess()).getLong("started_at")==tags.get(i).getLong("started_at"),"start time not preserved");
            }
        });
        h.runAtTickTime(25,()-> {
            try {
                h.assertTrue(anchors.getFirst().loadedRadius()>=0 && anchors.get(1).loadedRadius()<0 && anchors.get(2).loadedRadius()<0,"earliest anchor not retained after reversed reload");
                h.assertTrue(WorldlineAnchorBlockEntity.activeCount(owner)==1,"active registry not capped");h.succeed();
            } finally {ServerConfig.MAX_ANCHORS_PER_PLAYER.set(old);anchors.forEach(a->a.onBroken(h.getLevel()));}
        });
    }
}
