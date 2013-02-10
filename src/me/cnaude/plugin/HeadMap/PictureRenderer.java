/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package me.cnaude.plugin.HeadMap;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/**
 *
 * @author cnaude
 */
public class PictureRenderer extends MapRenderer {
    private Image img;
    private List<String> rendered = new ArrayList<String>();
    HMMain plugin;
     
    public PictureRenderer(String file, HMMain plug) {
        plugin = plug;
        readImage(file);
    }
 
    @Override
    public void render(MapView mv, MapCanvas mc, Player p) {
        // We only render once per player. Without this we lag like crazy.
        if (img != null && (!rendered.contains(p.getName()))) {
            mc.drawImage(0, 0, img);
            rendered.add(p.getName());
            plugin.logDebug("Rendered map " + mv.getId() + " for " + p.getName());
        } else {
            mv.getRenderers().clear();
        }
    }
    
    private void readImage(String file) {
        try {
            File f = new File(file);            
            if (f.exists()) {
                // Load from file, crop and then resize
                BufferedImage bi = ImageIO.read(f);
                img = bi.getSubimage(8, 8, 8, 8).getScaledInstance(128, 128, 0);                 
            } 
        } catch (IOException e) {  
            plugin.logError(e.getMessage());
        }        
    }
    
    public void removePlayer(String pName) {
        for (int x = rendered.size(); x <= 0; x--) {
            if (rendered.get(x).equals(pName)) {
                rendered.remove(x);
                plugin.logDebug("Removed player " + pName + " from map ");
            }                
        }
    }
}
