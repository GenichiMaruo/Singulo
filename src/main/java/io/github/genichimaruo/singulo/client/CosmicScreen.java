package io.github.genichimaruo.singulo.client;
import io.github.genichimaruo.singulo.energy.EnergyText;

import io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity;
import io.github.genichimaruo.singulo.cosmic.CosmicMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Quiet instrumentation: no full-screen flash, with a top-hanging fluid gauge. */
public final class CosmicScreen extends AbstractContainerScreen<CosmicMenu> {
    public CosmicScreen(CosmicMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=214;imageHeight=210;}
    private Component tr(String key,Object...args){return Component.translatable("gui.singulo.cosmic."+key,args);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        int x=leftPos,y=topPos,kind=menu.value(0);
        g.fill(x,y,x+imageWidth,y+imageHeight,0xFF101927);g.renderOutline(x,y,imageWidth,imageHeight,0xFF5B8295);
        g.drawWordWrap(font,title,x+14,y+9,186,0xFFE0F6FF);
        g.drawString(font,tr("state."+menu.value(1)),x+14,y+33,menu.value(1)==0?0xFF92D9BC:0xFFE3B45E,false);
        if(kind!=CosmicChamberBlockEntity.RESERVOIR){
            double k=menu.value(2)/(double)Math.max(1,menu.value(3));
            g.fill(x+14,y+47,x+200,y+57,0xFF080D15);g.fill(x+14,y+47,x+14+(int)(186*k),y+57,0xFF65C7DB);
            g.drawString(font,EnergyText.of(menu.value(2)*1_000_000L)+" / "+EnergyText.of(menu.value(3)*1_000_000L),x+14,y+61,0xFFD1DFE8,false);
            g.drawString(font,tr("rates",menu.value(4),menu.value(5)),x+14,y+75,0xFF9AB5CA,false);
        }
        if(kind==CosmicChamberBlockEntity.RESERVOIR){
            double k=menu.value(6)/(double)Math.max(1,menu.value(7));
            g.fill(x+14,y+47,x+39,y+111,0xFF080D15);g.fill(x+15,y+48,x+38,y+48+(menu.value(6)>0?Math.max(1,(int)(62*k)):0),0xFF65C7DB);
            Component fluid=tr("empty");
            if(menu.value(6)>0&&menu.value(11)>0){
                var type=net.minecraft.core.registries.BuiltInRegistries.FLUID.byId(menu.value(11)-1);
                fluid=new net.neoforged.neoforge.fluids.FluidStack(type,1).getHoverName();
                if(minecraft.level.getBlockEntity(menu.pos()) instanceof CosmicChamberBlockEntity be&&!be.tank().isEmpty()&&be.tank().getFluid().getFluid()==type)fluid=be.tank().getFluid().getHoverName();
            }
            g.drawWordWrap(font,fluid,x+48,y+49,150,0xFFD8EBF4);
            g.drawString(font,tr("amount",menu.value(6),menu.value(7)),x+48,y+73,0xFF9AB5CA,false);
            g.drawWordWrap(font,tr("ports"),x+48,y+89,148,0xFF9AB5CA);
        }
        if(kind==CosmicChamberBlockEntity.SPINDLE){
            for(int sx:new int[]{30,58,86,154})g.renderOutline(x+sx-1,y+91,18,18,0xFF5B8295);
            float k=menu.value(8)/(float)Math.max(1,menu.value(9));
            g.fill(x+110,y+96,x+144,y+104,0xFF080D15);g.fill(x+110,y+96,x+110+(int)(34*k),y+104,0xFFB0DFFF);
            if(menu.value(10)==0)g.drawString(font,tr("seed"),x+14,y+113,0xFFE3B45E,false);
            else g.drawString(font,tr("amount",menu.value(6),menu.value(7)),x+14,y+113,0xFF9AB5CA,false);
        }else if(kind==CosmicChamberBlockEntity.ACCUMULATOR)g.drawWordWrap(font,tr("ports"),x+14,y+94,186,0xFF9AB5CA);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)g.renderOutline(x+25+col*18,y+127+row*18,18,18,0xFF304959);
        for(int col=0;col<9;col++)g.renderOutline(x+25+col*18,y+185,18,18,0xFF405969);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);}
}
