package dev.srpau;

import me.ichun.mods.ichunutil.client.model.util.ModelHelper;
import me.ichun.mods.ichunutil.common.module.tabula.project.ProjectInfo;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.CubeInfo;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/** Discover every ModelRenderer and its hierarchy, including empty pivots and aliases.
 * The first registered part order is stable across instances of the same model.
 * No SRP class list or compiled SRP dependency. */
final class Rig {
    final String modelClass;
    final List<Bone> bones=new ArrayList<>();
    final List<Bone> roots=new ArrayList<>();
    final int textureWidth,textureHeight;
    final String signature;
    static final class Bone {
        final ModelRenderer part;
        final String name;
        final List<Bone> children=new ArrayList<>();
        final List<CubeInfo> geometry=new ArrayList<>();
        Bone(ModelRenderer part,String name){this.part=part;this.name=name;}
    }
    Rig(ModelBase model) {
        modelClass=model.getClass().getName();
        textureWidth=model.textureWidth; textureHeight=model.textureHeight;
        IdentityHashMap<ModelRenderer,String> names=new IdentityHashMap<>();
        for(Class<?> c=model.getClass();c!=null && ModelBase.class.isAssignableFrom(c);c=c.getSuperclass()){
            Field[] fields=c.getDeclaredFields();
            Arrays.sort(fields,Comparator.comparing(Field::getName));
            for(Field field:fields){
                if(Modifier.isStatic(field.getModifiers()) || !ModelRenderer.class.isAssignableFrom(field.getType()))continue;
                try {
                    field.setAccessible(true);
                    ModelRenderer part=(ModelRenderer)field.get(model);
                    if(part!=null && !names.containsKey(part))names.put(part,field.getName());
                }catch(ReflectiveOperationException|RuntimeException ignored){}
            }
        }
        IdentityHashMap<ModelRenderer,Bone> lookup=new IdentityHashMap<>();
        for(ModelRenderer part:model.boxList) discover(part,names,lookup);
        // Some models omit explicitly constructed parts from the inherited box list.
        List<Map.Entry<ModelRenderer,String>> fields=new ArrayList<>(names.entrySet());
        fields.sort(Comparator.comparing(Map.Entry::getValue));
        for(Map.Entry<ModelRenderer,String> f:fields)discover(f.getKey(),names,lookup);
        IdentityHashMap<Bone,Bone> parents=new IdentityHashMap<>();
        for(Bone bone:bones)if(bone.part.childModels!=null)for(ModelRenderer child:bone.part.childModels){
            Bone b=lookup.get(child);
            if(b==null)continue;
            Bone old=parents.put(b,bone);
            if(old!=null)throw new IllegalArgumentException("Model has a shared/cyclic child: "+b.name);
            bone.children.add(b);
        }
        for(Bone b:bones)if(!parents.containsKey(b))roots.add(b);
        Set<Bone> visited=Collections.newSetFromMap(new IdentityHashMap<Bone,Boolean>());
        for(Bone root:roots)verifyTree(root,visited);
        if(visited.size()!=bones.size()||bones.isEmpty())throw new IllegalArgumentException("Empty or cyclic model hierarchy");
        StringBuilder sig=new StringBuilder(modelClass);
        for(Bone b:bones){
            sig.append('|').append(b.name).append(':').append(parents.containsKey(b)?parents.get(b).name:"root")
                .append(':').append(b.part.cubeList.size());
            int i=0;
            for(ModelBox box:b.part.cubeList){
                CubeInfo cube=ModelHelper.createCubeInfoFromModelBox(b.part,box,b.name+"#box"+i++);
                cube.position=new double[3];cube.rotation=new double[3];
                b.geometry.add(cube);
                sig.append(':').append(Arrays.toString(cube.dimensions)).append(Arrays.toString(cube.offset))
                    .append(Arrays.toString(cube.txOffset)).append(cube.txMirror).append(cube.mcScale);
            }
        }
        signature=UUID.nameUUIDFromBytes(sig.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
    private void verifyTree(Bone bone,Set<Bone> visited){
        if(!visited.add(bone))throw new IllegalArgumentException("Cycle at "+bone.name);
        for(Bone child:bone.children)verifyTree(child,visited);
    }
    private void discover(ModelRenderer part,IdentityHashMap<ModelRenderer,String> names,IdentityHashMap<ModelRenderer,Bone> lookup){
        if(part==null||lookup.containsKey(part))return;
        if(bones.size()>=4096)throw new IllegalArgumentException("Model exceeds 4096 parts");
        String name=names.get(part);
        // Always include deterministic index: disambiguates shadowed fields and unnamed joints.
        Bone bone=new Bone(part,String.format(Locale.ROOT,"%04d_%s",bones.size(),name==null?"joint":name));
        bones.add(bone);lookup.put(part,bone);
        if(part.childModels!=null)for(ModelRenderer child:part.childModels)discover(child,names,lookup);
    }
    // Tabula's position units are model pixels. Native renderer offsets are world units at 1/16 scale.
    static double[] pose(ModelRenderer p){
        return new double[]{p.rotationPointX+16D*p.offsetX,p.rotationPointY+16D*p.offsetY,p.rotationPointZ+16D*p.offsetZ,
            Math.toDegrees(p.rotateAngleX),Math.toDegrees(p.rotateAngleY),Math.toDegrees(p.rotateAngleZ)};
    }
    double[][] sample(){
        double[][] result=new double[bones.size()][];
        for(int i=0;i<bones.size();i++) {
            result[i]=pose(bones.get(i).part);
            for(double d:result[i])if(!Double.isFinite(d))throw new IllegalArgumentException("Non-finite native pose");
        }
        return result;
    }
    ProjectInfo createProject(double[][] base){
        ProjectInfo project=new ProjectInfo(modelClass.substring(modelClass.lastIndexOf('.')+1),"SRP model; captured locally");
        project.projVersion=ProjectInfo.PROJ_VERSION;
        project.identifier="";
        project.textureWidth=textureWidth;project.textureHeight=textureHeight;
        project.metadata.add("srpau:rig:"+signature);
        IdentityHashMap<Bone,CubeInfo> joints=new IdentityHashMap<>();
        for(int i=0;i<bones.size();i++){
            Bone bone=bones.get(i);CubeInfo joint=new CubeInfo(bone.name);
            joint.dimensions=new int[3];
            joint.metadata.add("srpau:bone");
            joint.position=Arrays.copyOfRange(base[i],0,3);
            joint.rotation=Arrays.copyOfRange(base[i],3,6);
            joints.put(bone,joint);project.cubeCount++;
            for(CubeInfo original:bone.geometry){
                // Separate geometry from animated pivots. Do not lose extra boxes attached to one part.
                CubeInfo box=new CubeInfo(original.name);
                box.dimensions=original.dimensions.clone();box.offset=original.offset.clone();
                box.txOffset=original.txOffset.clone();box.txMirror=original.txMirror;
                joint.addChild(box);box.mcScale=original.mcScale;
                project.cubeCount++;
            }
        }
        for(Bone bone:bones){
            CubeInfo joint=joints.get(bone);
            for(Bone child:bone.children)joint.addChild(joints.get(child));
        }
        for(Bone b:roots)project.cubes.add(joints.get(b));
        return project;
    }
}
