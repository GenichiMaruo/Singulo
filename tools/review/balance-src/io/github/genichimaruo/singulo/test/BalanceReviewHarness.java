package io.github.genichimaruo.singulo.test;

import com.google.gson.GsonBuilder;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = Singulo.MODID)
public final class BalanceReviewHarness {
    private static List<String> options(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems()).map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).sorted().toList();
    }

    @SubscribeEvent
    public static void export(ServerStartedEvent event) throws Exception {
        String output = System.getProperty("singulo.balanceOutput");
        if (output == null || !(event.getServer() instanceof GameTestServer)) return;
        var level = event.getServer().overworld();
        var recipes = new ArrayList<Object>();
        for (var type : MachineType.values()) for (var holder : MachineBlockEntity.stationRecipes(level, type)) {
            var r = holder.value(); var row = new LinkedHashMap<String, Object>();
            row.put("id", holder.id().toString()); row.put("station",r.station()); row.put("stage",r.stage());
            row.put("ticks",r.time()); row.put("fe_per_tick",r.energy()); row.put("min_size",r.minSize());
            row.put("inputs", r.ingredients().stream().map(i -> Map.of("options",options(i.ingredient()), "count",i.count(),"uses",i.uses(),"tier",i.tier())).toList());
            row.put("restore",r.restore().map(BalanceReviewHarness::options).orElse(List.of()));
            row.put("fluids",r.fluidIngredients().stream().map(f -> Map.of("id",BuiltInRegistries.FLUID.getKey(f.fluid()).toString(),"count",f.amount())).toList());
            row.put("output",r.result().isEmpty()?"":BuiltInRegistries.ITEM.getKey(r.result().getItem()).toString());
            row.put("count",r.result().getCount());
            row.put("fluid_outputs",r.fluidResults().stream().map(f -> Map.of("id",BuiltInRegistries.FLUID.getKey(f.getFluid()).toString(),"count",f.getAmount())).toList());
            row.put("byproduct",r.byproduct().isEmpty()?"":BuiltInRegistries.ITEM.getKey(r.byproduct().getItem()).toString());
            r.mass().ifPresent(m -> row.put("mass",Map.of("amount",m.amount(),"metal",m.metalOnly())));
            recipes.add(row);
        }
        var structures = new LinkedHashMap<String,Object>();
        for (var kind : Blueprints.Kind.values()) {
            var sizes = new TreeSet<Integer>(); for(int size:kind.sizes()) sizes.add(size);
            if(kind==Blueprints.Kind.COOLING_TOWER)sizes.add(10);
            for(int size:sizes) {
                var parts = new TreeMap<String,Integer>();
                for(var state:Blueprints.layout(kind,BlockPos.ZERO,Direction.SOUTH,size).values()) {
                    if(!state.isAir())parts.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),1,Integer::sum);
                }
                structures.put(kind.id()+"/"+size,parts);
            }
        }
        var path = Path.of(output); Files.createDirectories(path.getParent());
        Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("recipes",recipes,"structures",structures)));
        Singulo.LOGGER.info("SINGULO_BALANCE_EXPORTED {} effective recipes, {} structures",recipes.size(),structures.size());
    }
}
