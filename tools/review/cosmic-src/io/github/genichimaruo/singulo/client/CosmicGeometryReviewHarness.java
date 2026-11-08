package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.Singulo;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import net.minecraft.world.phys.Vec3;

/** Checks the actual emitted, culled spool mesh; never included in the distribution. */
final class CosmicGeometryReviewHarness {
    static java.util.List<net.minecraft.client.renderer.ShaderInstance> shaders() throws Exception {
        var shaders=new ArrayList<net.minecraft.client.renderer.ShaderInstance>();
        for(var entry:java.util.List.of(new Object[]{GlowDepth.class,"shader"},new Object[]{JetForgeRenderTypes.class,"beamShader"})){
            var field=((Class<?>)entry[0]).getDeclaredField((String)entry[1]);field.setAccessible(true);
            var shader=(net.minecraft.client.renderer.ShaderInstance)field.get(null);
            if(shader==null||net.minecraft.client.Minecraft.getInstance().gameRenderer.getShader(shader.getName())!=shader)
                throw new IllegalStateException("Shader is missing or overwritten: "+entry[1]);
            shaders.add(shader);
        }
        if(shaders.stream().map(net.minecraft.client.renderer.ShaderInstance::getName).distinct().count()!=2)
            throw new IllegalStateException("Duplicate cosmic shader registry names");
        return shaders;
    }

    static void afterReload(java.util.List<net.minecraft.client.renderer.ShaderInstance> previous)throws Exception{
        var current=shaders();
        for(var old:previous){
            if(current.contains(old)||org.lwjgl.opengl.GL20.glIsProgram(old.getId()))
                throw new IllegalStateException("Cosmic shader program survived resource reload: "+old.getName());
        }
        Singulo.LOGGER.info("SINGULO_COSMIC_SHADER_RELOAD_COMPLETE 2 replaced and released programs");
    }

    static void verify() throws Exception {
        var cylinder = CosmicChamberRenderer.class.getDeclaredMethod("cylinder", PoseStack.class, VertexConsumer.class,
                float.class, float.class, float.class, float.class, int.class, int.class, int.class, int.class);
        cylinder.setAccessible(true);
        for (float angle : new float[]{0, 0.37F, 1.7F}) {
            var positions = new ArrayList<Vec3>();
            var normals = new ArrayList<Vec3>();
            VertexConsumer recorder = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                    new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                        if (method.getName().equals("vertex")) {
                            int start = args.length == 4 ? 1 : 0;
                            positions.add(new Vec3(((Number)args[start]).doubleValue(),
                                    ((Number)args[start+1]).doubleValue(), ((Number)args[start+2]).doubleValue()));
                        } else if (method.getName().equals("normal")) {
                            int start = args.length == 4 ? 1 : 0;
                            normals.add(new Vec3(((Number)args[start]).doubleValue(),
                                    ((Number)args[start+1]).doubleValue(), ((Number)args[start+2]).doubleValue()));
                        }
                        return proxy;
                    });
            cylinder.invoke(null, new PoseStack(), recorder, -1.45F, 1.45F, 1.72F, angle,
                    0x6A668A, 0x8A86AC, 0xF000F0, 255);
            if (positions.size() != 16 * 12 || normals.size() != positions.size()) {
                throw new IllegalStateException("Incomplete cylinder mesh: " + positions.size());
            }
            for (int i = 0; i < positions.size(); i += 4) {
                Vec3 edge1 = positions.get(i+1).subtract(positions.get(i));
                Vec3 edge2 = positions.get(i+2).subtract(positions.get(i));
                double dot = edge1.cross(edge2).dot(normals.get(i));
                if (dot <= 1.0E-6) {
                    throw new IllegalStateException("Inward spool quad " + i/4 + " angle=" + angle + " normal dot=" + dot);
                }
            }
        }
        Singulo.LOGGER.info("SINGULO_COSMIC_GEOMETRY_COMPLETE 144 outward quads");
        verifyPipeChevrons();
    }

    private static void verifyPipeChevrons() throws Exception {
        var draw=PipeSurveyRenderer.class.getDeclaredMethod("chevrons",PoseStack.class,VertexConsumer.class,Vec3.class,
                net.minecraft.core.Direction.class,boolean.class,float.class,int.class,Vec3.class);
        draw.setAccessible(true);
        for(var side:net.minecraft.core.Direction.values())for(boolean incoming:new boolean[]{false,true})for(int sign:new int[]{-1,1}){
            var vertices=new ArrayList<Vec3>();
            VertexConsumer recorder=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},
                    (proxy,method,args)->{
                        if(method.getName().equals("vertex")){
                            int start=args.length==4?1:0;
                            vertices.add(new Vec3(((Number)args[start]).doubleValue(),((Number)args[start+1]).doubleValue(),((Number)args[start+2]).doubleValue()));
                        }
                        return proxy;
                    });
            draw.invoke(null,new PoseStack(),recorder,Vec3.ZERO,side,incoming,0.37F,0x78E6FF,new Vec3(sign*3,sign*4,sign*5));
            if(vertices.size()!=256)throw new IllegalStateException("Pipe arrow vertex budget changed: "+vertices.size());
            for(var vertex:vertices){
                double radial=switch(side.getAxis()){
                    case X->Math.max(Math.abs(vertex.y),Math.abs(vertex.z));
                    case Y->Math.max(Math.abs(vertex.x),Math.abs(vertex.z));
                    case Z->Math.max(Math.abs(vertex.x),Math.abs(vertex.y));
                };
                if(radial<=3.0/16)throw new IllegalStateException("Survey arrow is buried in the pipe: "+side+" "+vertex);
            }
        }
        Singulo.LOGGER.info("SINGULO_PIPE_ARROW_GEOMETRY_COMPLETE 24 external-surface cases");
    }
}
