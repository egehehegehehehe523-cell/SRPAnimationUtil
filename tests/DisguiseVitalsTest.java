package dev.srpau;
public final class DisguiseVitalsTest {
    public static void main(String[] args){
        if(DisguiseVitals.damage(20,7)!=13||DisguiseVitals.damage(2,8)!=0)throw new AssertionError("shell damage");
        if(DisguiseVitals.heal(19.5F)!=20||DisguiseVitals.heal(10)!=11)throw new AssertionError("regen cap");
        if(DisguiseVitals.hit(4,9,4)!=0||DisguiseVitals.hit(7,9,4)!=3||DisguiseVitals.hit(4,0,7)!=4)throw new AssertionError("hurt window");
        System.out.println("Disguise health, regeneration and damage window passed");
    }
}
