package io.github.genichimaruo.singulo.cosmic;

import io.github.genichimaruo.singulo.multiblock.ShapeSpec;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;

/** Open silhouettes: hanging crown, compression yokes, and a long spinning rail. */
public final class CosmicLayouts {
    private CosmicLayouts() {}
    public static final ShapeSpec RESERVOIR = new ShapeSpec(5, 7, 5, 2, 0, 0, (x,y,z) -> {
        if (x==2 && y==0 && z==0) return ShapeSpec.Slot.CONTROLLER;
        if (y==6 && (x==0 || x==4 || z==0 || z==4 || x==2 && z==2)
                || (x==0 || x==4) && (z==0 || z==4)
                || y==0 && (x==0 || x==4 || z==0 || z==4)) return frame();
        return ShapeSpec.Slot.AIR;
    });
    public static final ShapeSpec ACCUMULATOR = new ShapeSpec(7, 7, 7, 3, 0, 0, (x,y,z) -> {
        if (x==3 && y==0 && z==0) return ShapeSpec.Slot.CONTROLLER;
        if (y==0 || y==6 || (x==0 || x==6) && (z==0 || z==6)) return frame();
        if ((y==2 || y==4) && (x==0 || x==6 || z==0 || z==6))
            return ShapeSpec.Slot.part(() -> SinguloBlocks.PASTA_LAMINATE.get());
        return ShapeSpec.Slot.AIR;
    });
    public static final ShapeSpec SPINDLE = new ShapeSpec(5, 7, 13, 2, 0, 0, (x,y,z) -> {
        if (x==2 && y==0 && z==0) return ShapeSpec.Slot.CONTROLLER;
        // Side service openings: power and coolant can approach from the front
        // without occupying the controller's item-in / item-out faces.
        if(y==0&&z==0&&(x==1||x==3))return ShapeSpec.Slot.IGNORE;
        boolean ring = (z==2 || z==11) && (Math.abs(x-2)==2 || Math.abs(y-3)==2) && y>=1 && y<=5;
        if (ring || y==0 && (x==0 || x==4 || z==0 || z==12)
                || (z==2 || z==11) && (x==0 || x==4) && y<=5) return frame();
        return ShapeSpec.Slot.AIR;
    });
    private static ShapeSpec.Slot frame() { return ShapeSpec.Slot.part(() -> SinguloBlocks.COSMIC_FRAME.get()); }
    public static ShapeSpec of(int kind) { return kind==0 ? RESERVOIR : kind==1 ? ACCUMULATOR : SPINDLE; }
}
