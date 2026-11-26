package dev.srpau;

import com.google.gson.Gson;
import me.ichun.mods.ichunutil.common.module.tabula.project.ProjectInfo;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.Animation;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.AnimationComponent;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.CubeInfo;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.CubeGroup;
import me.ichun.mods.tabula.client.gui.GuiWorkspace;
import me.ichun.mods.tabula.common.Tabula;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** A bone-only authoring view over an ordinary Tabula ProjectInfo. No alternate format. */
final class BoneEditor {
    final GuiWorkspace workspace;
    private final ProjectInfo project;
    private final String projectId;
    private final Minecraft mc=Minecraft.getMinecraft();
    private final FontRenderer font=mc.fontRenderer;
    private final List<Node> roots=new ArrayList<>(),bones=new ArrayList<>();
    private final List<CubeHit> hits=new ArrayList<>();
    private final Set<Node> collapsed=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
    private final List<Control> controls=new ArrayList<>();
    private final ModelBase mesh=new ModelBase(){};
    private net.minecraft.client.renderer.texture.DynamicTexture upgradedTexture;
    private final GuiTextField name;
    private final GuiTextField[] values=new GuiTextField[3];
    private final GuiTextField[] attachmentFields=new GuiTextField[3];
    private final PlayerAttachment playerDummy=new PlayerAttachment();
    private Node attachedBone;
    private boolean attachmentVisible=true,attachmentRotation=false,attachmentSettings=false;
    private Animation clip;
    private Node selected;
    private int frame,mode=1,axis=0,clipScroll,boneScroll;
    private boolean playing,leftDown,rightDown,dragTimeline,dirty,preview;
    private boolean autoKey=true,gizmoChanged;
    private TransformGizmo gizmo;
    private TransformGizmo.Drag gizmoDrag;
    private double[] dragOriginal;
    private boolean dragOriginalPreview;
    private int lastX,lastY;
    private float pitch=15,yaw=-35,zoom=1;
    private double cx,cy,cz,extent=32,floorY=24;
    private double timelineZoom=1,timelineStart=0;
    private int timelineEnd, timelineTop;
    private double[] proposed;
    private String status="Select a bone, then drag its coloured handles.";
    private int w,h,left,right,top=43,bottom;
    private static final int BG=0xff1c1e24,PANEL=0xff25272e,EDGE=0xff383c47,FG=0xffe4e9f0,MUTED=0xffa2acbc,ACCENT=0xff6b96f7;
    private static final class Node {
        final CubeInfo base,pose;
        final List<Node> children=new ArrayList<>();
        final ModelRenderer geometry;
        final boolean bone;
        final int depth;
        Node(CubeInfo base,ModelRenderer geometry,boolean bone,int depth){
            this.base=base;this.geometry=geometry;this.bone=bone;this.depth=depth;
            pose=new CubeInfo(base.name);
        }
    }
    // Project the actual animated cuboid, not the joint's pivot. Depth is interpolated
    // on its faces, so overlapping geometry selects the frontmost owning joint.
    private static final int[][] FACES={{0,2,3,1},{4,5,7,6},{0,1,5,4},{2,6,7,3},{0,4,6,2},{1,3,7,5}};
    private static final int[][] EDGES={{0,1},{0,2},{0,4},{1,3},{1,5},{2,3},{2,6},{3,7},{4,5},{4,6},{5,7},{6,7}};
    private static final class CubeHit {
        final Node owner;
        final double[][] points;
        CubeHit(Node owner,double[][] points){this.owner=owner;this.points=points;}
        double depthAt(double x,double y){
            double best=Double.NEGATIVE_INFINITY;
            for(int[] f:FACES){
                best=Math.max(best,triangle(points[f[0]],points[f[1]],points[f[2]],x,y));
                best=Math.max(best,triangle(points[f[0]],points[f[2]],points[f[3]],x,y));
            }
            return best;
        }
        static double triangle(double[] a,double[] b,double[] c,double x,double y){
            double det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1]);
            if(Math.abs(det)<1e-8)return Double.NEGATIVE_INFINITY;
            double u=((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/det;
            double v=((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/det;
            double t=1-u-v;
            return Math.min(u,Math.min(v,t))>=-1e-6?u*a[2]+v*b[2]+t*c[2]:Double.NEGATIVE_INFINITY;
        }
    }
    private static final class Control{
        final int x,y,w,h;final Runnable action;
        Control(int x,int y,int w,int h,Runnable a){this.x=x;this.y=y;this.w=w;this.h=h;action=a;}
        boolean contains(int a,int b){return a>=x&&a<x+w&&b>=y&&b<y+h;}
    }
    BoneEditor(GuiWorkspace workspace,ProjectInfo original){
        this.workspace=workspace;projectId=original.identifier;
        project=new Gson().fromJson(original.getAsJson(),ProjectInfo.class);
        project.identifier=projectId;project.bufferedTexture=original.bufferedTexture;
        if(AdventurerBends.upgrade(project)){
            dirty=true;status="Added elbow and knee joints; Apply & back to save.";
        }
        mesh.textureWidth=project.textureWidth;mesh.textureHeight=project.textureHeight;
        for(CubeInfo cube:project.cubes)roots.add(node(cube,0));
        for(CubeGroup group:project.cubeGroups)addGroup(group);
        if(bones.isEmpty())throw new IllegalArgumentException("No imported joints found");
        name=new GuiTextField(0,font,0,0,120,16);name.setMaxStringLength(80);
        for(int i=0;i<3;i++){values[i]=new GuiTextField(i+1,font,0,0,47,16);values[i].setMaxStringLength(18);}
        for(int i=0;i<3;i++){attachmentFields[i]=new GuiTextField(10+i,font,0,0,47,16);attachmentFields[i].setMaxStringLength(18);}
        refreshAttachmentFields();
        selected=null;
        if(!project.anims.isEmpty())choose(project.anims.get(0));else refreshFields();
        fit();
        leftDown=Mouse.isButtonDown(0);rightDown=Mouse.isButtonDown(1);
    }
    private void addGroup(CubeGroup group){
        for(CubeInfo c:group.cubes)roots.add(node(c,0));
        for(CubeGroup g:group.cubeGroups)addGroup(g);
    }
    private Node node(CubeInfo c,int depth){
        boolean joint=(c.metadata!=null&&c.metadata.contains("srpau:bone"))||c.name.matches("^[0-9]{4,}_.*")&&!c.name.contains("#box");
        ModelRenderer geometry=null;
        if(c.dimensions[0]!=0||c.dimensions[1]!=0||c.dimensions[2]!=0){
            geometry=new ModelRenderer(mesh,c.txOffset[0],c.txOffset[1]);geometry.mirror=c.txMirror;
            geometry.addBox((float)c.offset[0],(float)c.offset[1],(float)c.offset[2],c.dimensions[0],c.dimensions[1],c.dimensions[2],(float)c.mcScale);
        }
        Node n=new Node(c,geometry,joint,depth);
        if(joint)bones.add(n);
        for(CubeInfo child:c.getChildren())n.children.add(node(child,depth+(joint?1:0)));
        return n;
    }
    private void choose(Animation a){
        finishGizmo(false);
        clip=a;frame=0;timelineStart=0;timelineZoom=1;playing=false;preview=false;proposed=null;
        name.setText(a==null?"":a.name);refreshFields();
    }
    private void select(Node n){finishGizmo(false);selected=n;preview=false;proposed=null;refreshFields();}
    private int length(){return clip==null?40:Math.max(1,clip.getLength());}
    private void seek(int t){finishGizmo(false);frame=Math.max(0,Math.min(length(),t));playing=false;preview=false;proposed=null;refreshFields();}
    void tick(){
        if(!playing||clip==null)return;
        if(frame>=length()){
            if(clip.loops)frame=0;else{playing=false;refreshFields();}
        }else frame++;
        if(playing)refreshFields();
    }
    private void eval(Node n,float time){
        CubeInfo b=n.base,p=n.pose;
        System.arraycopy(b.position,0,p.position,0,3);System.arraycopy(b.rotation,0,p.rotation,0,3);
        System.arraycopy(b.scale,0,p.scale,0,3);p.opacity=b.opacity;p.hidden=b.hidden;
        if(clip!=null){
            List<AnimationComponent> track=clip.sets.get(b.identifier);
            if(track!=null)for(AnimationComponent c:track)if(!c.hidden&&c.length>0)c.animate(p,time);
        }
    }
    private double[] channel(Node n){return mode==0?n.pose.position:mode==1?n.pose.rotation:n.pose.scale;}
    private void refreshFields(){
        if(selected==null){for(GuiTextField v:values)v.setText("");return;}
        eval(selected,frame);
        double[] v=channel(selected);
        for(int i=0;i<3;i++)values[i].setText(String.format(Locale.ROOT,"%.4f",v[i]));
    }
    private void setMode(int m){finishGizmo(false);mode=m;preview=false;proposed=null;refreshFields();}
    private void togglePlayback(){
        finishGizmo(false);
        if(clip==null)return;
        if(!playing&&frame>=length())frame=0;
        playing=!playing;preview=false;proposed=null;refreshFields();
    }
    private double[] readFields(){
        double[] v=new double[3];
        for(int i=0;i<3;i++){
            v[i]=Double.parseDouble(values[i].getText());
            if(!Double.isFinite(v[i])||Math.abs(v[i])>100000||(mode==2&&v[i]<=0))throw new IllegalArgumentException("Invalid transform");
        }
        return v;
    }
    private void adjust(int i,double delta){
        if(selected==null||clip==null)return;
        if(playing){playing=false;refreshFields();}
        try{proposed=readFields();}catch(RuntimeException ex){eval(selected,frame);proposed=channel(selected).clone();}
        proposed[i]+=delta;if(mode==2)proposed[i]=Math.max(.01,proposed[i]);
        preview=true;
        for(int k=0;k<3;k++)values[k].setText(String.format(Locale.ROOT,"%.4f",proposed[k]));
    }
    private void keyframe(){
        if(selected==null||clip==null){status="Create or select a clip first.";return;}
        try{
            double[] desired=preview&&proposed!=null?proposed.clone():readFields();
            eval(selected,frame);double[] current=channel(selected),delta=new double[3];
            boolean changed=false;
            for(int i=0;i<3;i++){delta[i]=desired[i]-current[i];changed|=Math.abs(delta[i])>1e-7;}
            if(!changed){status="No transform change to key.";return;}
            ArrayList<AnimationComponent> track=clip.sets.computeIfAbsent(selected.base.identifier,k->new ArrayList<>());
            int prev=0,next=Integer.MAX_VALUE;
            for(AnimationComponent c:track){
                if(c.hidden||c.name.equals("Duration")||c.name.equals("End"))continue;
                for(int t:new int[]{c.startKey,c.startKey+c.length}){
                    if(t<frame)prev=Math.max(prev,t);if(t>frame)next=Math.min(next,t);
                }
            }
            // Tabula components accumulate their changes. Add a local correction ramp in/out,
            // leaving the preexisting native curve and all unaffected components intact.
            AnimationComponent in=new AnimationComponent("Key "+frame+" "+modeName(),Math.max(1,frame-prev),frame==0?0:prev);
            double[] dest=component(in,frame==0);
            for(int i=0;i<3;i++)dest[i]=delta[i];
            track.add(in);
            if(next!=Integer.MAX_VALUE){
                AnimationComponent out=new AnimationComponent("Return "+frame+" "+modeName(),next-frame,frame);
                double[] reverse=component(out,false);
                for(int i=0;i<3;i++)reverse[i]=-delta[i];track.add(out);
            }
            Collections.sort(track);dirty=true;preview=false;proposed=null;refreshFields();
            status="Keyed "+selected.base.name+" at tick "+frame+".";
        }catch(RuntimeException ex){status="Enter finite transform values (scale > 0).";}
    }
    private double[] component(AnimationComponent c,boolean offset){
        if(mode==0)return offset?c.posOffset:c.posChange;
        if(mode==1)return offset?c.rotOffset:c.rotChange;
        return offset?c.scaleOffset:c.scaleChange;
    }
    private String modeName(){return mode==0?"Position":mode==1?"Rotation":"Scale";}
    private void duration(Animation a,int t){
        a.sets.computeIfAbsent(bones.get(0).base.identifier,k->new ArrayList<>()).add(new AnimationComponent("Duration",1,Math.max(0,t-1)));
    }
    private void newClip(){
        String label=name.getText().trim();
        if(label.isEmpty()||clip!=null&&label.equals(clip.name))label="New animation";
        String base=label;int i=2;
        while(hasName(label))label=base+" "+i++;
        Animation a=new Animation(label);duration(a,40);project.anims.add(a);dirty=true;choose(a);
        status="New native Tabula clip. Select a bone and set keys.";
    }
    private boolean hasName(String name){for(Animation a:project.anims)if(a.name.equals(name))return true;return false;}
    private void rename(){if(clip!=null&&!name.getText().trim().isEmpty()){clip.name=name.getText().trim();dirty=true;}}
    void dispose(){if(upgradedTexture!=null){upgradedTexture.deleteGlTexture();upgradedTexture=null;}}
    private void apply(){
        if(!dirty){BoneEditorEvents.close();return;}
        ProjectInfo current=workspace.getOpenProject();
        if(current==null||!projectId.equals(current.identifier)){status="Original project changed; cancel instead.";return;}
        Tabula.proxy.tickHandlerClient.mainframe.overrideProject(projectId,project.getAsJson(),project.bufferedTexture);
        BoneEditorEvents.close();
    }
    private void button(int x,int y,int width,int height,String text,boolean on,Runnable action){
        Gui.drawRect(x,y,x+width,y+height,on?0xff365879:EDGE);
        Gui.drawRect(x+1,y+1,x+width-1,y+height-1,on?0xff34577a:0xff353c47);
        font.drawString(font.trimStringToWidth(text,width-4),x+3,y+(height-8)/2,FG);
        controls.add(new Control(x,y,width,height,action));
    }
    private void text(String s,int x,int y,int color,int max){font.drawString(font.trimStringToWidth(s,Math.max(0,max)),x,y,color);}
    private String boneName(Node n){return n==null?"No bone selected":n.base.name.replaceFirst("^[0-9]{4,}_","");}
    private void visibleBones(Node n,List<Node> result){
        if(n.bone)result.add(n);
        if(!collapsed.contains(n))for(Node c:n.children)visibleBones(c,result);
    }
    private boolean hasBoneChildren(Node n){
        for(Node c:n.children)if(c.bone||hasBoneChildren(c))return true;
        return false;
    }
    private void checker(){
        Gui.drawRect(left,top,right,bottom,0xff191b20);
        int cell=10;
        for(int y=top;y<bottom;y+=cell)for(int x=left;x<right;x+=cell)
            if(((x-left)/cell+(y-top)/cell)%2==0)Gui.drawRect(x,y,Math.min(right,x+cell),Math.min(bottom,y+cell),0xff1e2026);
    }
    private double[] attachmentChannel(){return attachmentRotation?playerDummy.rotation:playerDummy.position;}
    private void refreshAttachmentFields(){
        double[] v=attachmentChannel();
        for(int i=0;i<3;i++)attachmentFields[i].setText(String.format(Locale.ROOT,"%.3f",v[i]));
    }
    private void attachPlayer(){
        if(selected==null){status="Select a bone to attach the player.";return;}
        attachedBone=selected;attachmentVisible=true;attachmentSettings=h>=400;playerDummy.reset();refreshAttachmentFields();
        status="Player attached to "+boneName(attachedBone)+" (preview only).";
    }
    private void detachPlayer(){
        attachedBone=null;for(GuiTextField f:attachmentFields)f.setFocused(false);
        status="Player preview detached.";
    }
    private void readAttachment(){
        double[] next=new double[3];
        for(int i=0;i<3;i++){
            next[i]=Double.parseDouble(attachmentFields[i].getText());
            if(!Double.isFinite(next[i])||Math.abs(next[i])>100000)throw new IllegalArgumentException("Invalid offset");
        }
        System.arraycopy(next,0,attachmentChannel(),0,3);
    }
    private void adjustAttachment(int axis,int direction){
        double[] v=attachmentChannel();
        v[axis]+=direction*(attachmentRotation?5D:.5D);refreshAttachmentFields();
    }
    private int attachmentPanel(){
        int width=w-right;
        panelTitle("PLAYER ATTACHMENT",right,80,width);
        button(right+5,101,Math.max(45,width-10),16,attachedBone==null?"Attach player":"Attach to selection",false,this::attachPlayer);
        if(attachedBone==null)return 123;
        text("On: "+boneName(attachedBone),right+5,121,FG,width-10);
        button(right+5,134,48,16,attachmentVisible?"Hide":"Show",attachmentVisible,()->attachmentVisible=!attachmentVisible);
        button(right+56,134,49,16,"Detach",false,this::detachPlayer);
        button(right+5,153,Math.max(45,width-10),16,attachmentSettings?"Collapse settings":"Adjust attachment",attachmentSettings,()->{attachmentSettings=!attachmentSettings;for(GuiTextField f:attachmentFields)f.setFocused(false);});
        if(!attachmentSettings)return 173;
        button(right+5,172,48,16,"Offset",!attachmentRotation,()->{attachmentRotation=false;refreshAttachmentFields();});
        button(right+56,172,49,16,"Rotate",attachmentRotation,()->{attachmentRotation=true;refreshAttachmentFields();});
        for(int i=0;i<3;i++){
            final int k=i;int y=192+i*18;
            text("XYZ".substring(i,i+1),right+5,y+4,MUTED,12);
            attachmentFields[i].x=right+18;attachmentFields[i].y=y;attachmentFields[i].drawTextBox();
            button(right+68,y,16,16,"-",false,()->adjustAttachment(k,-1));
            button(right+87,y,16,16,"+",false,()->adjustAttachment(k,1));
        }
        button(right+5,248,20,16,"-",false,()->playerDummy.size=Math.max(.1,playerDummy.size-.1));
        text(String.format(Locale.ROOT,"%.1fx",playerDummy.size),right+29,252,FG,42);
        button(right+65,248,20,16,"+",false,()->playerDummy.size=Math.min(10,playerDummy.size+.1));
        button(right+5,267,48,16,"Reset",false,()->{playerDummy.reset();refreshAttachmentFields();});
        button(right+56,267,49,16,"Copy",false,()->{
            net.minecraft.client.gui.GuiScreen.setClipboardString(
                "bone="+attachedBone.base.name+"; id="+attachedBone.base.identifier+
                "; offset="+Arrays.toString(playerDummy.position)+"; rotation="+Arrays.toString(playerDummy.rotation)+"; scale="+playerDummy.size);
            status="Attachment parameters copied (model pixels / degrees).";
        });
        return 289;
    }
    private void panelTitle(String title,int x,int y,int width){
        Gui.drawRect(x,y,x+width,y+17,0xff292c34);text(title,x+6,y+5,MUTED,width-12);
    }
    void draw(int mx,int my,float partial){
        w=workspace.width;h=workspace.height;
        left=Math.min(200,Math.max(116,w*23/100));right=w-Math.min(175,Math.max(110,w/5));
        bottom=Math.max(top+85,h-Math.max(110,h*34/100));
        controls.clear();hits.clear();gizmo=null;
        Gui.drawRect(0,0,w,h,BG);
        Gui.drawRect(0,0,w,22,PANEL);
        Gui.drawRect(0,23,left,h,PANEL);Gui.drawRect(right,23,w,h,PANEL);
        Gui.drawRect(left,23,right,top,0xff262932);
        text("SRP | ANIMATE",6,7,ACCENT,105);
        button(w-144,3,72,16,"Apply & back",false,this::apply);
        button(w-68,3,64,16,"Cancel",false,()->{if(!dirty||status.equals("Cancel again to discard all changes."))BoneEditorEvents.close();else status="Cancel again to discard all changes.";});
        text(dirty?"Unsaved edits":"Native Tabula project",115,7,MUTED,Math.max(0,w-265));
        panelTitle("ANIMATIONS",0,23,left);
        button(5,43,24,16,"+",false,this::newClip);
        button(32,43,45,16,"Rename",false,this::rename);
        button(80,43,32,16,"Loop",clip!=null&&clip.loops,()->{if(clip!=null){clip.loops=!clip.loops;dirty=true;}});
        int keyPanel=Math.max(102,Math.min(h-172,h*48/100));
        int rows=Math.max(1,(keyPanel-64)/16);
        clipScroll=Math.max(0,Math.min(clipScroll,Math.max(0,project.anims.size()-rows)));
        for(int i=clipScroll;i<project.anims.size()&&i<clipScroll+rows;i++){
            Animation a=project.anims.get(i);int y=63+(i-clipScroll)*16;
            if(a==clip)Gui.drawRect(0,y,left,y+15,0xff414955);
            text((a==clip?"> ":"  ")+a.name,6,y+4,FG,left-12);
            controls.add(new Control(0,y,left,15,()->choose(a)));
        }
        panelTitle("KEYFRAME",0,keyPanel,left);
        name.x=5;name.y=keyPanel+21;name.drawTextBox();
        text(boneName(selected),6,keyPanel+43,FG,left-12);
        text(modeName()+"  |  Tick "+frame,6,keyPanel+56,MUTED,left-12);
        for(int i=0;i<3;i++){
            final int k=i;int y=keyPanel+70+i*21;
            button(5,y,16,16,"XYZ".substring(i,i+1),axis==i,()->axis=k);
            values[i].x=24;values[i].y=y;values[i].drawTextBox();
            button(75,y,16,16,"-",false,()->adjust(k,-step()));
            button(94,y,16,16,"+",false,()->adjust(k,step()));
        }
        button(5,keyPanel+135,68,17,"Set key",preview,this::keyframe);
        button(5,keyPanel+155,80,16,"Auto key",autoKey,()->autoKey=!autoKey);
        panelTitle("BONE",right,23,w-right);
        text(boneName(selected),right+6,46,FG,w-right-12);
        text("Pivot / parent transform",right+6,60,MUTED,w-right-12);
        int outlinerTop=attachmentPanel();
        panelTitle("OUTLINER",right,outlinerTop,w-right);
        List<Node> visible=new ArrayList<>();
        for(Node root:roots)visibleBones(root,visible);
        int boneRows=Math.max(0,(h-outlinerTop-22)/14);
        boneScroll=Math.max(0,Math.min(boneScroll,Math.max(0,visible.size()-boneRows)));
        for(int i=boneScroll;i<visible.size()&&i<boneScroll+boneRows;i++){
            Node n=visible.get(i);int y=outlinerTop+22+(i-boneScroll)*14,x=right+5+Math.min(6,n.depth)*6;
            if(n==selected)Gui.drawRect(right,y,w,y+13,0xff414955);
            if(hasBoneChildren(n)){
                text(collapsed.contains(n)?">":"v",x,y+3,MUTED,8);
                controls.add(new Control(x,y,10,13,()->{if(!collapsed.add(n))collapsed.remove(n);}));
            }
            Gui.drawRect(x+11,y+4,x+19,y+10,0xff88b6db);
            text(boneName(n),x+22,y+3,n==selected?ACCENT:FG,w-x-24);
            controls.add(new Control(x+10,y,Math.max(0,w-x-10),13,()->select(n)));
        }
        checker();
        renderModel(partial,mx,my);
        button(left+4,25,37,15,"Move",mode==0,()->setMode(0));
        button(left+44,25,49,15,"Rotate",mode==1,()->setMode(1));
        button(left+96,25,40,15,"Scale",mode==2,()->setMode(2));
        button(left+139,25,29,15,"Fit",false,this::fit);
        text("Parent",left+173,29,MUTED,right-left-177);
        text("RMB orbit | wheel zoom",left+5,bottom-12,MUTED,right-left-10);
        drawTimeline();
        text(status,5,h-11,MUTED,left-10);
        input(mx,my,timelineTop,timelineX(),timelineWidth());
    }
    private int timelineX(){return left+Math.min(100,Math.max(65,(right-left)/4));}
    private int timelineWidth(){return Math.max(1,right-timelineX()-5);}
    private double visibleTicks(){return Math.max(4,Math.max(40,length())/timelineZoom);}
    private double maxTimelineStart(){return Math.max(0,length()-visibleTicks());}
    private void clampTimeline(){timelineStart=Math.max(0,Math.min(maxTimelineStart(),timelineStart));}
    private int timeX(double t){return timelineX()+(int)Math.round((t-timelineStart)*timelineWidth()/visibleTicks());}
    private int timeAt(int x){return (int)Math.round(timelineStart+(x-timelineX())*visibleTicks()/timelineWidth());}
    private static boolean changed(double[] a){for(double v:a)if(Math.abs(v)>1e-7)return true;return false;}
    private static boolean hasChannel(AnimationComponent c,int channel){
        return channel==0?changed(c.posChange)||changed(c.posOffset):channel==1?changed(c.rotChange)||changed(c.rotOffset):changed(c.scaleChange)||changed(c.scaleOffset);
    }
    private void diamond(int x,int y,int color){
        Gui.drawRect(x,y-3,x+1,y+4,color);Gui.drawRect(x-1,y-2,x+2,y+3,color);Gui.drawRect(x-2,y-1,x+3,y+2,color);Gui.drawRect(x-3,y,x+4,y+1,color);
    }
    private void drawTimeline(){
        Gui.drawRect(left,bottom,right,h,0xff202229);
        panelTitle("TIMELINE",left,bottom,right-left);
        int y=bottom+19,mid=(left+right)/2;
        button(mid-44,y,24,15,"|<",false,()->seek(0));
        button(mid-17,y,34,15,playing?"Pause":">",playing,this::togglePlayback);
        button(mid+20,y,24,15,">|",false,()->seek(length()));
        button(left+4,y,40,15,"+20t",false,()->{if(clip!=null&&length()<1200){duration(clip,Math.min(1200,length()+20));dirty=true;}});
        text(String.format(Locale.ROOT,"%.2fs / %.2fs",frame/20D,length()/20D),Math.max(left+46,right-91),y+4,FG,88);
        timelineTop=bottom+39;
        timelineEnd=h-15;
        int tx=timelineX(),tw=timelineWidth(),ty=timelineTop;
        clampTimeline();
        Gui.drawRect(left,ty,tx-1,timelineEnd,PANEL);
        Gui.drawRect(tx,ty,right,timelineEnd,0xff191b20);
        text("Time (s)",left+5,ty+4,MUTED,tx-left-8);
        int ruler=ty+20;
        int rowHeight=Math.max(9,Math.min(20,(timelineEnd-ruler-14)/3));
        String[] channels={"Position","Rotation","Scale"};
        text(boneName(selected),left+5,ruler+2,FG,tx-left-8);
        for(int i=0;i<3;i++){
            final int ch=i;int row=ruler+13+i*rowHeight;
            if(mode==i)Gui.drawRect(left,row,tx-1,row+rowHeight,0xff363c49);
            text(channels[i],left+12,row+3,mode==i?ACCENT:MUTED,tx-left-15);
            controls.add(new Control(left,row,tx-left,rowHeight,()->setMode(ch)));
            Gui.drawRect(tx,row,right,row+1,0xff2d3039);
        }
        double ticks=visibleTicks(),target=ticks/Math.max(2,tw/45);
        int major=1;for(int candidate:new int[]{1,2,5,10,20,40,100,200,400,800}){major=candidate;if(candidate>=target)break;}
        int first=(int)Math.floor(timelineStart/major)*major;
        for(int t=first;t<=timelineStart+ticks;t+=major){
            int px=timeX(t);if(px<tx||px>=right)continue;
            Gui.drawRect(px,ty,px+1,timelineEnd,0xff30333c);
            text(String.format(Locale.ROOT,"%s",java.math.BigDecimal.valueOf(t/20D).stripTrailingZeros().toPlainString()),px+2,ty+3,MUTED,right-px-2);
            if(major>=5)for(int minor=1;minor<5;minor++){
                int sx=timeX(t+major*minor/5D);if(sx>=tx&&sx<right)Gui.drawRect(sx,ty+14,sx+1,ty+19,EDGE);
            }
        }
        if(clip!=null&&selected!=null){
            List<AnimationComponent> track=clip.sets.get(selected.base.identifier);
            if(track!=null)for(int ch=0;ch<3;ch++){
                int ky=ruler+13+ch*rowHeight+rowHeight/2;
                Set<Integer> keys=new TreeSet<>();
                for(AnimationComponent c:track)if(!c.hidden&&hasChannel(c,ch)){keys.add(c.startKey);keys.add(c.startKey+c.length);}
                Integer previous=null;
                for(int t:keys){
                    int px=timeX(t);
                    if(previous!=null){int a=Math.max(tx,timeX(previous)),b=Math.min(right-1,px);if(b>a)Gui.drawRect(a,ky,b,ky+1,0xff526179);}
                    if(px>=tx+3&&px<right-3)diamond(px,ky,t==frame?0xfff0cd87:0xffa8b6d4);
                    previous=t;
                }
            }
        }
        int pos=timeX(frame);if(pos>=tx&&pos<right){Gui.drawRect(pos,ty,pos+1,timelineEnd,ACCENT);Gui.drawRect(pos-3,ty,pos+4,ty+4,ACCENT);}
        text("Wheel: zoom  |  Shift+wheel: pan",left+5,h-11,MUTED,right-left-10);
    }
    private double step(){return mode==0?.5:mode==1?5:.05;}
    private boolean viewport(int x,int y){return x>=left&&x<right&&y>=top&&y<bottom;}
    private void input(int mx,int my,int ty,int tx,int tw){
        boolean l=Mouse.isButtonDown(0),r=Mouse.isButtonDown(1);
        int wheel=Mouse.getDWheel();
        if(wheel!=0&&gizmoDrag==null){
            if(viewport(mx,my))zoom=(float)Math.max(.15,Math.min(8,zoom*Math.pow(1.12,wheel/120D)));
            else if(mx>=timelineX()&&mx<right&&my>=timelineTop){
                double anchor=timelineStart+(mx-timelineX())*visibleTicks()/timelineWidth();
                if(net.minecraft.client.gui.GuiScreen.isShiftKeyDown())timelineStart-=Integer.signum(wheel)*visibleTicks()*.15;
                else{timelineZoom=Math.max(1,Math.min(16,timelineZoom*Math.pow(1.25,wheel/120D)));timelineStart=anchor-(mx-timelineX())*visibleTicks()/timelineWidth();}
                clampTimeline();
            }
            else if(mx<left)clipScroll-=Integer.signum(wheel)*3;
            else if(mx>=right)boneScroll-=Integer.signum(wheel)*3;
        }
        if(gizmoDrag==null&&r&&rightDown&&viewport(mx,my)){yaw+=(mx-lastX)*.6F;pitch=Math.max(-89,Math.min(89,pitch+(my-lastY)*.6F));}
        if(l&&!leftDown){
            boolean field=name.mouseClicked(mx,my,0);for(GuiTextField v:values)field|=v.mouseClicked(mx,my,0);
            for(GuiTextField f:attachmentFields){if(attachedBone!=null&&attachmentSettings)field|=f.mouseClicked(mx,my,0);else f.setFocused(false);}
            if(!field){
                boolean clicked=false;
                for(Control c:controls)if(c.contains(mx,my)){c.action.run();clicked=true;break;}
                if(!clicked&&mx>=tx&&mx<right&&my>=ty&&my<timelineEnd){dragTimeline=true;seek(timeAt(mx));}
                else if(!clicked&&viewport(mx,my)&&!beginGizmo(mx,my)){
                    Node best=null;double depth=Double.NEGATIVE_INFINITY;
                    for(CubeHit hit:hits){double z=hit.depthAt(mx,my);if(z>depth){depth=z;best=hit.owner;}}
                    if(best!=selected)select(best);
                }
            }
        }else if(l&&leftDown){
            if(dragTimeline)seek(timeAt(mx));
            if(gizmoDrag!=null)updateGizmo(mx,my);
        }
        if(!l){dragTimeline=false;finishGizmo(true);}
        leftDown=l;rightDown=r;lastX=mx;lastY=my;
    }
    void key(char c,int k){
        if(k==Keyboard.KEY_ESCAPE){
            if(gizmoDrag!=null){finishGizmo(false);return;}
            if(dirty){status="Use Apply & back, or Cancel twice to discard.";}else BoneEditorEvents.close();
            return;
        }
        if(gizmoDrag!=null)return;
        if(attachedBone!=null&&attachmentSettings)for(GuiTextField f:attachmentFields)if(f.isFocused()){
            if(k==Keyboard.KEY_RETURN){try{readAttachment();}catch(RuntimeException ignored){}refreshAttachmentFields();f.setFocused(false);}
            else if(f.textboxKeyTyped(c,k))try{readAttachment();}catch(RuntimeException ignored){}
            return;
        }
        if(name.isFocused()){if(k==Keyboard.KEY_RETURN){rename();name.setFocused(false);}else name.textboxKeyTyped(c,k);return;}
        for(GuiTextField v:values)if(v.isFocused()){
            if(k==Keyboard.KEY_RETURN){keyframe();v.setFocused(false);}
            else if(v.textboxKeyTyped(c,k))try{proposed=readFields();preview=true;playing=false;}catch(RuntimeException ignored){}
            return;
        }
        if(k==Keyboard.KEY_G)setMode(0);else if(k==Keyboard.KEY_R)setMode(1);else if(k==Keyboard.KEY_S)setMode(2);
        else if(k==Keyboard.KEY_X)axis=0;else if(k==Keyboard.KEY_Y)axis=1;else if(k==Keyboard.KEY_Z)axis=2;
        else if(k==Keyboard.KEY_LEFT)seek(frame-1);else if(k==Keyboard.KEY_RIGHT)seek(frame+1);
        else if(k==Keyboard.KEY_SPACE)togglePlayback();
        else if(k==Keyboard.KEY_RETURN)keyframe();
    }
    private void fit(){
        double[] bounds={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(Node n:roots)bounds(n,projectScale(),bounds);
        if(!Double.isFinite(bounds[0])){bounds=new double[]{-8,0,-8,8,24,8};}
        cx=(bounds[0]+bounds[3])/2;cy=(bounds[1]+bounds[4])/2;cz=(bounds[2]+bounds[5])/2;
        extent=Math.max(16,Math.max(bounds[3]-bounds[0],Math.max(bounds[4]-bounds[1],bounds[5]-bounds[2])));
        floorY=bounds[4]+.08;
        zoom=1;
    }
    private void bounds(Node n,Mat parent,double[] b){
        Mat m=parent.mul(Mat.local(n.base));
        if(n.geometry!=null)for(int mask=0;mask<8;mask++){
            double[] p=m.point(n.base.offset[0]+((mask&1)!=0?n.base.dimensions[0]:0),
                n.base.offset[1]+((mask&2)!=0?n.base.dimensions[1]:0),n.base.offset[2]+((mask&4)!=0?n.base.dimensions[2]:0));
            for(int i=0;i<3;i++){b[i]=Math.min(b[i],p[i]);b[i+3]=Math.max(b[i+3],p[i]);}
        }
        for(Node c:n.children)bounds(c,m,b);
    }
    private Mat projectScale(){
        return Mat.scale(1D/validScale(project.scale[0]),1D/validScale(project.scale[1]),1D/validScale(project.scale[2]));
    }
    private static double validScale(double s){return Double.isFinite(s)&&Math.abs(s)>1e-6?s:1;}
    private void renderModel(float partial,int mx,int my){
        if(right<=left||bottom<=top)return;
        float time=frame+(playing&&frame<length()?Math.max(0,Math.min(1,partial)):0);
        double size=Math.min(right-left,bottom-top)*.72/extent*zoom;
        double vx=(left+right)/2D,vy=(top+bottom)/2D;
        Mat view=Mat.translate(vx,vy,0).mul(Mat.scale(size,size,size)).mul(Mat.rx(pitch)).mul(Mat.ry(yaw)).mul(Mat.translate(-cx,-cy,-cz)).mul(projectScale());
        if(project.bufferedTexture!=null&&project.metadata.contains(AdventurerBends.MARK)&&upgradedTexture==null)
            upgradedTexture=new net.minecraft.client.renderer.texture.DynamicTexture(project.bufferedTexture);
        int texture=upgradedTexture!=null?upgradedTexture.getGlTextureId():workspace.windowTexture.imageId;
        boolean textured=texture!=-1;
        int sx=(int)(left*mc.displayWidth/(double)w),sy=(int)((h-bottom)*mc.displayHeight/(double)h);
        int sw=(int)((right-left)*mc.displayWidth/(double)w),sh=(int)((bottom-top)*mc.displayHeight/(double)h);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(sx,sy,sw,sh);
        try{
        GlStateManager.pushMatrix();
        try{
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);GlStateManager.enableDepth();GlStateManager.depthMask(true);
            GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO);
            if(textured){GlStateManager.enableTexture2D();GlStateManager.bindTexture(texture);}else GlStateManager.disableTexture2D();
            GlStateManager.translate(vx,vy,200);GlStateManager.scale(size,size,size);
            GlStateManager.rotate(pitch,1,0,0);GlStateManager.rotate(yaw,0,1,0);GlStateManager.translate(-cx,-cy,-cz);
            drawFloor();
            if(textured){GlStateManager.enableTexture2D();GlStateManager.bindTexture(texture);}else GlStateManager.disableTexture2D();
            GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);
            GlStateManager.scale(1D/validScale(project.scale[0]),1D/validScale(project.scale[1]),1D/validScale(project.scale[2]));
            for(Node n:roots)renderNode(n,view,null,time,textured);
        }finally{
            GlStateManager.popMatrix();GlStateManager.disableDepth();GlStateManager.enableTexture2D();
            GlStateManager.enableCull();GlStateManager.color(1,1,1,1);
        }
            drawOutline();
            drawGizmo(mx,my);
            drawAxes();
        }finally{GL11.glDisable(GL11.GL_SCISSOR_TEST);}
    }
    // Stable world-space floor at the rest model's feet, independent of animation.
    private void drawFloor(){
        GlStateManager.disableTexture2D();
        double unit=Math.max(1,Math.pow(2,Math.floor(Math.log(Math.max(1,extent/24))/Math.log(2))));
        int radius=32;double x0=Math.floor(cx/unit)*unit,z0=Math.floor(cz/unit)*unit;
        GL11.glLineWidth(1F);
        GL11.glBegin(GL11.GL_LINES);
        for(int i=-radius;i<=radius;i++){
            boolean major=i%8==0;
            GL11.glColor4f(major?.30F:.21F,major?.32F:.23F,major?.36F:.26F,1F);
            double x=x0+i*unit,z=z0+i*unit;
            GL11.glVertex3d(x,floorY,z0-radius*unit);GL11.glVertex3d(x,floorY,z0+radius*unit);
            GL11.glVertex3d(x0-radius*unit,floorY,z);GL11.glVertex3d(x0+radius*unit,floorY,z);
        }
        GL11.glEnd();
        GL11.glColor4f(1,1,1,1);GlStateManager.color(1,1,1,1);
    }
    private void drawOutline(){
        if(selected==null)return;
        // Draw at the actual projected depth, so hidden edges do not show through other cubes.
        GlStateManager.enableDepth();GlStateManager.depthMask(false);GlStateManager.disableTexture2D();
        GL11.glLineWidth(1.8F);
        GlStateManager.color(.55F,.76F,1F,1F);
        try{
            boolean own=false;for(CubeHit hit:hits)if(hit.owner==selected){own=true;break;}
            GL11.glBegin(GL11.GL_LINES);
            for(CubeHit hit:hits){
                if(hit.owner!=selected&&(own||!descendant(selected,hit.owner)))continue;
                for(int[] edge:EDGES)for(int idx:edge){
                    double[] v=hit.points[idx];GL11.glVertex3d(v[0],v[1],v[2]+200.15);
                }
            }
            GL11.glEnd();
        }finally{
            GL11.glLineWidth(1F);GlStateManager.depthMask(true);GlStateManager.disableDepth();
            GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
        }
    }
    private boolean descendant(Node root,Node target){
        if(root==target)return true;for(Node child:root.children)if(descendant(child,target))return true;return false;
    }
    private static double[] axisVector(Mat m,int axis){
        double[] origin=m.point(0,0,0);
        return TransformGizmo.sub(m.point(axis==0?1:0,axis==1?1:0,axis==2?1:0),origin);
    }
    private void makeGizmo(Mat parent,CubeInfo p){
        Mat pivot=parent.mul(Mat.translate(p.position[0],p.position[1],p.position[2]));
        Mat z=pivot.mul(Mat.rz(p.rotation[2]));
        Mat y=z.mul(Mat.ry(p.rotation[1]));
        Mat x=y.mul(Mat.rx(p.rotation[0]));
        gizmo=new TransformGizmo(mode,pivot.point(0,0,0));
        for(int a=0;a<3;a++){
            if(mode==TransformGizmo.ROTATE){
                // Actual Euler derivative axes for T * Rz * Ry * Rx * S:
                // X follows Z and Y, Y follows Z, Z is in parent space.
                Mat basis=a==0?y:a==1?z:pivot;
                gizmo.ring(a,axisVector(basis,(a+1)%3),axisVector(basis,(a+2)%3));
            }else{
                gizmo.axis(a,axisVector(mode==TransformGizmo.MOVE?pivot:x,a));
            }
        }
    }
    private static final int[] GIZMO_COLORS={0xffe34d50,0xff85df53,0xff5b83ff};
    private void drawGizmo(int mx,int my){
        if(gizmo==null)return;
        TransformGizmo.Pick hover=viewport(mx,my)?visibleGizmo(mx,my):null;
        GlStateManager.enableDepth();GlStateManager.depthMask(false);GlStateManager.disableTexture2D();
        boolean culled=GL11.glIsEnabled(GL11.GL_CULL_FACE);GlStateManager.disableCull();
        try{
            for(TransformGizmo.Handle handle:gizmo.handles){
                int a=handle.axis;
                boolean active=gizmoDrag!=null&&gizmoDrag.axis==a;
                boolean hovered=gizmoDrag==null&&hover!=null&&hover.handle.axis==a;
                int c=active||hovered?0xffffdf75:GIZMO_COLORS[a];
                GlStateManager.color(((c>>16)&255)/255F,((c>>8)&255)/255F,(c&255)/255F,1F);
                drawGizmoStroke(handle.points,active||hovered?5.5:3.5);
                if(mode!=TransformGizmo.ROTATE){
                    double[] o=handle.points[0],e=handle.points[1];
                    double dx=e[0]-o[0],dy=e[1]-o[1],n=Math.hypot(dx,dy);
                    dx/=n;dy/=n;
                    if(mode==TransformGizmo.MOVE){
                        GL11.glBegin(GL11.GL_TRIANGLES);
                        GL11.glVertex3d(e[0]+dx*7,e[1]+dy*7,e[2]+200);
                        GL11.glVertex3d(e[0]-dx*7-dy*5,e[1]-dy*7+dx*5,e[2]+200);
                        GL11.glVertex3d(e[0]-dx*7+dy*5,e[1]-dy*7-dx*5,e[2]+200);
                        GL11.glEnd();
                    }else{
                        GL11.glBegin(GL11.GL_QUADS);
                        GL11.glVertex3d(e[0]-4,e[1]-4,e[2]+200);GL11.glVertex3d(e[0]+4,e[1]-4,e[2]+200);
                        GL11.glVertex3d(e[0]+4,e[1]+4,e[2]+200);GL11.glVertex3d(e[0]-4,e[1]+4,e[2]+200);
                        GL11.glEnd();
                    }
                }
            }
            GlStateManager.color(.9F,.92F,.96F,1F);
            GL11.glPointSize(4F);GL11.glBegin(GL11.GL_POINTS);
            GL11.glVertex3d(gizmo.origin[0],gizmo.origin[1],gizmo.origin[2]+200);GL11.glEnd();GL11.glPointSize(1F);
        }finally{
            GL11.glLineWidth(1F);GlStateManager.depthMask(true);GlStateManager.disableDepth();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
            if(culled)GlStateManager.enableCull();
        }
    }
    private void drawGizmoStroke(double[][] points,double width){
        // GL line widths are often clamped to one physical pixel; filled
        // triangles give a reliable GUI-pixel thickness at every GUI scale.
        boolean culled=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.disableCull();
        try{
            GL11.glBegin(GL11.GL_TRIANGLES);
            for(int i=0;i<points.length-1;i++){
                double[] a=points[i],b=points[i+1];
                double dx=b[0]-a[0],dy=b[1]-a[1],n=Math.hypot(dx,dy);
                if(n<1e-8)continue;
                double ox=-dy/n*width*.5,oy=dx/n*width*.5;
                GL11.glVertex3d(a[0]+ox,a[1]+oy,a[2]+200);
                GL11.glVertex3d(a[0]-ox,a[1]-oy,a[2]+200);
                GL11.glVertex3d(b[0]+ox,b[1]+oy,b[2]+200);
                GL11.glVertex3d(b[0]+ox,b[1]+oy,b[2]+200);
                GL11.glVertex3d(a[0]-ox,a[1]-oy,a[2]+200);
                GL11.glVertex3d(b[0]-ox,b[1]-oy,b[2]+200);
            }
            GL11.glEnd();
        }finally{if(culled)GlStateManager.enableCull();}
    }
    private TransformGizmo.Pick visibleGizmo(int x,int y){
        TransformGizmo.Pick p=gizmo.pick(x,y);
        if(p==null)return null;
        for(CubeHit cube:hits)if(cube.depthAt(x,y)>p.depth+.15)return null;
        return p;
    }
    private boolean beginGizmo(int mx,int my){
        if(gizmo==null||selected==null||clip==null)return false;
        TransformGizmo.Pick pick=visibleGizmo(mx,my);
        if(pick==null)return false;
        if(playing){playing=false;refreshFields();}
        dragOriginalPreview=preview;
        eval(selected,frame);
        dragOriginal=(preview&&proposed!=null?proposed:channel(selected)).clone();
        proposed=dragOriginal.clone();
        gizmoDrag=new TransformGizmo.Drag(gizmo,pick,mx,my);
        axis=pick.handle.axis;gizmoChanged=false;preview=true;
        return true;
    }
    private void updateGizmo(int mx,int my){
        if(gizmoDrag==null)return;
        double delta=gizmoDrag.delta(mx,my);
        if(!Double.isFinite(delta)||Math.abs(delta)<1e-8)return;
        int a=gizmoDrag.axis;
        double next=proposed[a]+delta;
        proposed[a]=Math.max(mode==TransformGizmo.SCALE?.01:-100000,Math.min(100000,next));
        gizmoChanged=true;
        for(int i=0;i<3;i++)values[i].setText(String.format(Locale.ROOT,"%.4f",proposed[i]));
    }
    private void finishGizmo(boolean commit){
        if(gizmoDrag==null)return;
        gizmoDrag=null;
        if(!commit){
            preview=dragOriginalPreview;proposed=preview?dragOriginal.clone():null;
            if(preview)for(int i=0;i<3;i++)values[i].setText(String.format(Locale.ROOT,"%.4f",proposed[i]));
            else refreshFields();
        }else if(gizmoChanged&&autoKey)keyframe();
        else if(!gizmoChanged){preview=dragOriginalPreview;proposed=preview?dragOriginal.clone():null;}
        gizmoChanged=false;dragOriginal=null;
    }
    private void drawAxes(){
        int x=right-28,y=bottom-30;
        Mat m=Mat.rx(pitch).mul(Mat.ry(yaw));
        int[] colors={0xffdf6969,0xff8cc477,0xff7298ee};
        for(int a=0;a<3;a++){
            double[] p=m.point(a==0?15:0,a==1?-15:0,a==2?15:0);
            GlStateManager.disableTexture2D();GL11.glLineWidth(2F);
            int c=colors[a];GlStateManager.color(((c>>16)&255)/255F,((c>>8)&255)/255F,(c&255)/255F,1F);
            GL11.glBegin(GL11.GL_LINES);GL11.glVertex2d(x,y);GL11.glVertex2d(x+p[0],y+p[1]);GL11.glEnd();
            GL11.glLineWidth(1F);GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
            text("XYZ".substring(a,a+1),(int)(x+p[0])-2,(int)(y+p[1])-3,c,10);
        }
    }
    private void renderNode(Node n,Mat parent,Node owner,float time,boolean textured){
        eval(n,time);
        if(n==selected&&preview&&proposed!=null)System.arraycopy(proposed,0,channel(n),0,3);
        CubeInfo p=n.pose;
        Mat local=parent.mul(Mat.local(p));
        if(n==selected&&!p.hidden)makeGizmo(parent,p);
        if(n.bone)owner=n;
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(p.position[0],p.position[1],p.position[2]);
            GlStateManager.rotate((float)p.rotation[2],0,0,1);GlStateManager.rotate((float)p.rotation[1],0,1,0);GlStateManager.rotate((float)p.rotation[0],1,0,0);
            GlStateManager.scale(p.scale[0],p.scale[1],p.scale[2]);
            if(!p.hidden&&n.geometry!=null&&p.opacity>0){
                GlStateManager.color(textured?1F:.75F,textured?1F:.80F,textured?1F:.86F,(float)Math.max(0,Math.min(1,p.opacity/100)));
                for(ModelBox b:n.geometry.cubeList)b.render(Tessellator.getInstance().getBuffer(),1F);
                if(owner!=null){
                    double[][] corners=new double[8][];
                    double inflate=n.base.mcScale;
                    for(int mask=0;mask<8;mask++)corners[mask]=local.point(
                        n.base.offset[0]+((mask&1)!=0?n.base.dimensions[0]+inflate:-inflate),
                        n.base.offset[1]+((mask&2)!=0?n.base.dimensions[1]+inflate:-inflate),
                        n.base.offset[2]+((mask&4)!=0?n.base.dimensions[2]+inflate:-inflate));
                    hits.add(new CubeHit(owner,corners));
                }
            }
            if(!p.hidden&&n==attachedBone&&attachmentVisible)playerDummy.render(textured);
            if(!p.hidden)for(Node c:n.children)renderNode(c,local,owner,time,textured);
        }finally{GlStateManager.popMatrix();}
    }
    /** Same T * Rz * Ry * Rx * S local transform as viewport; only for picking and bounds. */
    private static final class Mat{
        final double[] m;
        Mat(double... a){m=a;}
        static Mat identity(){return scale(1,1,1);}
        static Mat translate(double x,double y,double z){return new Mat(1,0,0,x,0,1,0,y,0,0,1,z,0,0,0,1);}
        static Mat scale(double x,double y,double z){return new Mat(x,0,0,0,0,y,0,0,0,0,z,0,0,0,0,1);}
        static Mat rx(double d){double r=Math.toRadians(d),c=Math.cos(r),s=Math.sin(r);return new Mat(1,0,0,0,0,c,-s,0,0,s,c,0,0,0,0,1);}
        static Mat ry(double d){double r=Math.toRadians(d),c=Math.cos(r),s=Math.sin(r);return new Mat(c,0,s,0,0,1,0,0,-s,0,c,0,0,0,0,1);}
        static Mat rz(double d){double r=Math.toRadians(d),c=Math.cos(r),s=Math.sin(r);return new Mat(c,-s,0,0,s,c,0,0,0,0,1,0,0,0,0,1);}
        static Mat local(CubeInfo p){return translate(p.position[0],p.position[1],p.position[2]).mul(rz(p.rotation[2])).mul(ry(p.rotation[1])).mul(rx(p.rotation[0])).mul(scale(p.scale[0],p.scale[1],p.scale[2]));}
        Mat mul(Mat b){double[] a=new double[16];for(int r=0;r<4;r++)for(int c=0;c<4;c++)for(int k=0;k<4;k++)a[r*4+c]+=m[r*4+k]*b.m[k*4+c];return new Mat(a);}
        double[] point(double x,double y,double z){return new double[]{m[0]*x+m[1]*y+m[2]*z+m[3],m[4]*x+m[5]*y+m[6]*z+m[7],m[8]*x+m[9]*y+m[10]*z+m[11]};}
    }
}
