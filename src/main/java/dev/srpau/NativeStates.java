package dev.srpau;

import net.minecraft.client.model.ModelBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Discover status branch constants directly from the loaded model bytecode.
 * No entity-specific code or status name table. Source analysis follows local loads/stores.
 * Unsupported arithmetic-derived states or missing resources fall back to live recording. */
final class NativeStates {
    private static final String BASE="com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase";
    static final class Sources extends SourceInterpreter {
        Sources(){super(Opcodes.ASM5);}
        @Override public SourceValue copyOperation(AbstractInsnNode node,SourceValue value){return value;}
    }
    static SortedSet<Integer> discover(Class<?> model){
        SortedSet<Integer> result=new TreeSet<>();
        result.add(0);
        for(Class<?> type=model;type!=null && ModelBase.class.isAssignableFrom(type);type=type.getSuperclass()){
            if(type==ModelBase.class)break;
            String resource="/"+type.getName().replace('.','/')+".class";
            try(InputStream in=type.getResourceAsStream(resource)){
                if(in==null)continue;
                ClassNode node=new ClassNode();
                new ClassReader(in).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                for(Object obj:node.methods){
                    MethodNode method=(MethodNode)obj;
                    if(method.instructions.size()==0)continue;
                    boolean reads=false;
                    for(AbstractInsnNode ins:method.instructions.toArray())if(isStatusCall(ins)){reads=true;break;}
                    if(!reads)continue;
                    try{
                        Analyzer<SourceValue> analyzer=new Analyzer<>(new Sources());
                        Frame<SourceValue>[] frames=analyzer.analyze(node.name,method);
                        AbstractInsnNode[] ins=method.instructions.toArray();
                        for(int i=0;i<ins.length;i++){
                            Frame<SourceValue> frame=frames[i];
                            if(frame==null)continue;
                            int op=ins[i].getOpcode(),n=frame.getStackSize();
                            if(op>=Opcodes.IF_ICMPEQ && op<=Opcodes.IF_ICMPLE && n>=2){
                                SourceValue a=frame.getStack(n-2),c=frame.getStack(n-1);
                                if(isStatus(a))constants(c,result);
                                if(isStatus(c))constants(a,result);
                            }else if(op>=Opcodes.IFEQ && op<=Opcodes.IFLE && n>=1 && isStatus(frame.getStack(n-1))){
                                result.add(0);
                            }else if(n>=1 && ins[i] instanceof TableSwitchInsnNode && isStatus(frame.getStack(n-1))){
                                TableSwitchInsnNode table=(TableSwitchInsnNode)ins[i];
                                for(int k=Math.max(-128,table.min);k<=Math.min(127,table.max);k++)result.add(k);
                            }else if(n>=1 && ins[i] instanceof LookupSwitchInsnNode && isStatus(frame.getStack(n-1))){
                                for(Object key:((LookupSwitchInsnNode)ins[i]).keys)add(result,(Integer)key);
                            }
                        }
                    }catch(AnalyzerException|RuntimeException ignored){}
                }
            }catch(Exception|LinkageError ignored){}
        }
        return result;
    }
    private static boolean isStatusCall(AbstractInsnNode node){
        if(!(node instanceof MethodInsnNode))return false;
        MethodInsnNode m=(MethodInsnNode)node;
        return "getParasiteStatus".equals(m.name)&&"()B".equals(m.desc);
    }
    private static boolean isStatus(SourceValue value){
        if(value==null)return false;
        for(AbstractInsnNode node:value.insns)if(isStatusCall(node))return true;
        return false;
    }
    private static void add(Set<Integer> result,int n){if(n>=-128&&n<=127)result.add(n);}
    private static void constants(SourceValue value,Set<Integer> result){
        if(value==null)return;
        for(AbstractInsnNode node:value.insns){
            int op=node.getOpcode();
            if(op>=Opcodes.ICONST_M1&&op<=Opcodes.ICONST_5)add(result,op-Opcodes.ICONST_0);
            else if(node instanceof IntInsnNode && (op==Opcodes.BIPUSH||op==Opcodes.SIPUSH))add(result,((IntInsnNode)node).operand);
            else if(node instanceof LdcInsnNode && ((LdcInsnNode)node).cst instanceof Integer)add(result,(Integer)((LdcInsnNode)node).cst);
        }
    }

    /** Isolated instance: never inserted into the world, ticked or sent to the server.
     * Only the common SRP preview inputs are changed, never the user's real entity. */
    static final class Preview {
        final EntityLivingBase entity;
        final Method status;
        final Field still,aniTicks;
        Preview(EntityLivingBase original) throws Exception {
            Class<?> base=Class.forName(BASE,false,original.getClass().getClassLoader());
            if(!base.isInstance(original))throw new IllegalArgumentException("Not an EntityParasiteBase");
            Constructor<?> ctor=original.getClass().getDeclaredConstructor(World.class);
            ctor.setAccessible(true);
            entity=(EntityLivingBase)ctor.newInstance(original.world);
            // No setPosition, NBT loading, world registration, update or network packets.
            status=base.getMethod("setParasiteStatus",int.class);
            still=base.getDeclaredField("still");still.setAccessible(true);
            aniTicks=base.getDeclaredField("aniticks");aniTicks.setAccessible(true);
        }
        void configure(int state,boolean idle,int tick) throws Exception{
            if(tick==0)status.invoke(entity,state);
            still.setBoolean(entity,idle);
            aniTicks.setFloat(entity,idle?tick*.0012F:0F);
            entity.ticksExisted=tick;
        }
    }
}
