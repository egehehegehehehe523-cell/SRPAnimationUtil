package dev.srpau;

/** iChunUtil 7.2.2 truncates recovered floating UVs rather than rounding.
 * Native 1.10.9 ModelInfPlayer's leg at (46,0) becomes (46,-1)
 * because (4F/55F)*55F is 3.9999998F. Correct known native origins
 * only when the imported coordinates differ by at most one pixel.
 * Preserve other valid, intentionally changed offsets. */
final class AdventurerUV {
    private static final int[][] NATIVE={{40,16},{0,26},{46,0},{0,2}};
    static int[] correct(int limb,int[] uv,int width,int height){
        if(uv==null||uv.length<2)return null;
        int u=uv[0],v=uv[1];
        int[] expected=NATIVE[limb];
        if(width==64&&height==55&&Math.abs(u-expected[0])<=1&&Math.abs(v-expected[1])<=1){
            u=expected[0];v=expected[1];
        }
        if(u<0||v<0||u>width-16||v>height-16)return null;
        return new int[]{u,v};
    }
}
