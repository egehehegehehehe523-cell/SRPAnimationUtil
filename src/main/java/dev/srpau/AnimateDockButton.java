package dev.srpau;

import me.ichun.mods.ichunutil.client.gui.window.element.Element;
import me.ichun.mods.ichunutil.client.gui.window.element.ElementButton;
import me.ichun.mods.ichunutil.client.gui.window.element.ElementButtonTextured;
import me.ichun.mods.ichunutil.client.gui.window.element.ElementToggle;
import me.ichun.mods.tabula.client.gui.window.WindowTopDock;
import net.minecraft.util.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** Real native Tabula icon button; runs last in the dock's resize pass.
 * Reserve its own slot and reflow the stock controls only when necessary. */
public final class AnimateDockButton extends ElementButtonTextured {
    public AnimateDockButton(WindowTopDock dock,int id){
        super(dock,0,0,id,true,0,0,"srpau.animate",
            new ResourceLocation("srpanimationutil","textures/gui/animate_blue.png"));
    }
    @Override public void resized(){
        List<Element> icons=new ArrayList<>();
        ElementToggle block=null;
        ElementButton folder=null;
        Element exit=null;
        for(Element e:parent.elements){
            if(e==this)continue;
            if(e.id==WindowTopDock.ID_WOOD && e instanceof ElementToggle)block=(ElementToggle)e;
            else if(e.id==WindowTopDock.ID_OPEN_WORKING_DIR && e instanceof ElementButton)folder=(ElementButton)e;
            else if(e.id==WindowTopDock.ID_EXIT_TABULA)exit=e;
            else if(e instanceof ElementButtonTextured)icons.add(e);
        }
        // 20 stock left icons + our icon. Keep the normal 20-pixel size whenever
        // possible; compact the two text controls before shrinking the icon row.
        int w=parent.width,count=icons.size()+1;
        int reserve=Math.min(209,Math.max(112,w-count*20-2));
        reserve=Math.min(reserve,Math.max(60,w/2));
        int slot=Math.max(1,Math.min(20,(w-reserve-2)/count));
        int x=0;
        for(Element e:icons){e.posX=x;e.posY=(20-slot)/2;e.width=slot;e.height=slot;x+=slot;}
        posX=x+1;posY=(20-slot)/2;width=slot;height=slot;
        // Right-aligned native controls are reflowed after their own resized()
        // calls, so their hitboxes cannot cover our icon on GUI scale changes.
        int exitSize=Math.min(20,Math.max(8,w-reserve));
        int exitX=w-exitSize;
        if(exit!=null){exit.posX=exitX;exit.posY=(20-exitSize)/2;exit.width=exitSize;exit.height=exitSize;}
        int rightStart=Math.max(posX+width+1,w-reserve);
        int blockWidth=Math.min(40,Math.max(18,(exitX-rightStart-5)/3));
        if(block!=null){block.posX=rightStart;block.posY=4;block.width=blockWidth;block.height=12;
            block.text=blockWidth>=40?"topdock.wood":"B";}
        if(folder!=null){
            folder.posX=rightStart+blockWidth+5;folder.posY=4;
            folder.width=Math.max(0,exitX-folder.posX-4);folder.height=12;
            folder.text=folder.width>=140?"topdock.openWorkingDir":folder.width>=42?"Folder":"Dir";
        }
    }
    @Override public boolean mouseInBoundary(int x,int y){
        return x>=posX && x<posX+width && y>=posY && y<posY+height;
    }
}
