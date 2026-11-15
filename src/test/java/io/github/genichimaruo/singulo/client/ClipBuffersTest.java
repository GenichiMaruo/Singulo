package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Headless regression checks for printer clipping and all forwarded vertex attributes. */
public final class ClipBuffersTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static final class Output implements VertexConsumer {
        float x,y,z,u,v,nx,ny,nz,width;int color,overlay,light;
        public VertexConsumer addVertex(float x,float y,float z){this.x=x;this.y=y;this.z=z;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){color=a<<24|r<<16|g<<8|b;return this;}
        public VertexConsumer setColor(int c){color=c;return this;}
        public VertexConsumer setUv(float u,float v){this.u=u;this.v=v;return this;}
        public VertexConsumer setUv1(int u,int v){overlay=v<<16|u;return this;}
        public VertexConsumer setUv2(int u,int v){light=v<<16|u;return this;}
        public VertexConsumer setNormal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
        public VertexConsumer setLineWidth(float w){width=w;return this;}
    }
    public static void main(String[] args){
        Output out=new Output();
        VertexConsumer below=ClipBuffers.below(type->out,2).getBuffer(null);
        below.addVertex(3,5,7);check(out.x==3&&out.y==2&&out.z==7,"above plane clamped, XZ preserved");
        below.addVertex(3,-4,7);check(out.y==-4,"below plane unchanged");
        VertexConsumer above=ClipBuffers.above(out,2);
        above.addVertex(1,-2,3);check(out.y==2,"lower hologram clamped");
        above.addVertex(1,8,3);check(out.y==8,"upper hologram unchanged");
        below.setColor(0x80402010).setUv(.25F,.75F).setOverlay(123).setLight(456).setNormal(1,0,0).setLineWidth(2);
        check(out.color==0x80402010,"packed ARGB preserved");check(out.u==.25F&&out.v==.75F,"UV preserved");
        check(out.overlay==123&&out.light==456,"light and overlay preserved");check(out.nx==1&&out.ny==0&&out.nz==0&&out.width==2,"normal and line width preserved");
        below.setColor(10,20,30,40);check(out.color==0x280A141E,"channel ARGB preserved");
        System.out.println("PASS ClipBuffers "+checks+" assertions");
    }
}
