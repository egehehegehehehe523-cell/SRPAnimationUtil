package dev.srpau;
final class DisguiseVitals {
    static final float MAX=20;
    static final int REGEN_DELAY=100, REGEN_INTERVAL=40, HURT_WINDOW=10;
    static float heal(float health){return Math.min(MAX,health+1);}
    static float hit(float amount,int cooldown,float last){
        if(!Float.isFinite(amount)||amount<=0)return 0;
        return cooldown>0?Math.max(0,amount-last):amount;
    }
    static float damage(float health,float amount){return Math.max(0,health-amount);}
}
