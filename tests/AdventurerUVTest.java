package dev.srpau;
import java.util.Arrays;
public final class AdventurerUVTest {
    private static void eq(int[] a,int... b){if(!Arrays.equals(a,b))throw new AssertionError(Arrays.toString(a)+" != "+Arrays.toString(b));}
    public static void main(String[] args){
        int broken=(int)((4F/55F)*55F)-4;
        if(broken!=-1)throw new AssertionError("Expected iChunUtil truncation");
        eq(AdventurerUV.correct(2,new int[]{46,broken},64,55),46,0);
        eq(AdventurerUV.correct(0,new int[]{40,16},64,55),40,16);
        eq(AdventurerUV.correct(1,new int[]{0,26},64,55),0,26);
        eq(AdventurerUV.correct(3,new int[]{0,2},64,55),0,2);
        eq(AdventurerUV.correct(2,new int[]{20,20},64,55),20,20);
        if(AdventurerUV.correct(2,new int[]{46,-5},64,55)!=null)throw new AssertionError("Invalid UV accepted");
        System.out.println("Adventurer UV regression tests passed");
    }
}
