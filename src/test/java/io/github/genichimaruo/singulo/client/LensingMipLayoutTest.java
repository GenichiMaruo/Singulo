package io.github.genichimaruo.singulo.client;

public final class LensingMipLayoutTest {
    public static void main(String[] args) {
        int checks=0;
        for (int[] size : new int[][]{{1280,800},{854,480},{1920,1080},{800,1280},{1,4096},{4096,1},{1,1},{1024,1024}}) {
            int n=LensingMipLayout.levels(size[0],size[1]);
            for(int i=0;i<n;i++) {
                if((size[0]>>i)<1 || (size[1]>>i)<1) throw new AssertionError("Zero dimension mip");
                checks++;
            }
            if((size[0]>>n)>0 && (size[1]>>n)>0) throw new AssertionError("Unnecessarily truncated mip chain");
            checks++;
        }
        for(int[] size:new int[][]{{0,1},{1,0},{-1,1}}) {
            try { LensingMipLayout.levels(size[0],size[1]); throw new AssertionError("Invalid dimensions accepted"); }
            catch(IllegalArgumentException expected) { checks++; }
        }
        System.out.println("Lensing mip layout: "+checks+" checks PASS");
    }
}
