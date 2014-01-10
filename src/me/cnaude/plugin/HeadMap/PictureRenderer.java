package me.cnaude.plugin.HeadMap;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
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
    private final String type;
    private final List<String> rendered = new ArrayList<String>();
    HMMain plugin;
     
    public PictureRenderer(String file, HMMain plugin, String type) {
        this.plugin = plugin;
        this.type = type;
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
    
    private BufferedImage flipH(BufferedImage img) {        
        AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
        tx.translate(-img.getWidth(null), 0);
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(img, null);
    }
    
    private void readImage(String file) {
        plugin.logDebug("ReadImage: " + file);
        try {
            File f = new File(file);            
            if (f.exists()) {
                // Load from file, crop and then resize
                BufferedImage bi = ImageIO.read(f);
                if (type.equals("body")) {
                    BufferedImage face = bi.getSubimage(8, 8, 8, 8);
                    BufferedImage faceAcc = bi.getSubimage(40, 8, 8, 8);
                    BufferedImage leftLeg = bi.getSubimage(4, 20, 4, 12);
                    BufferedImage rightLeg = flipH(leftLeg);
                    BufferedImage leftArm = bi.getSubimage(44, 20, 4, 12);
                    BufferedImage rightArm = flipH(leftArm);
                    BufferedImage body = bi.getSubimage(20, 20, 8, 12);
                    BufferedImage combined = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);                    
                    Graphics2D g = combined.createGraphics(); 
                    int adj = 8;
                    g.drawImage(face, 4 + adj, 0, null);
                    g.drawImage(faceAcc, 4 + adj, 0, null);
                    g.drawImage(body, 4 + adj, 8, null);
                    g.drawImage(leftArm, 0 + adj, 8, null);
                    g.drawImage(rightArm, 12 + adj, 8, null);
                    g.drawImage(leftLeg, 4 + adj, 20, null);
                    g.drawImage(rightLeg, 8 + adj, 20, null);  
                    img = combined.getScaledInstance(128, 128, 0);
                } else if (type.equals("image")) {
                    img = bi.getScaledInstance(128, 128, 0);
                } else if (type.equals(("mob"))) {                    
                    if (f.getName().matches("wither.*")) {
                        img = bi.getSubimage(8, 43, 8, 8).getScaledInstance(128, 128, 0);
                    } else if (f.getName().matches("(redcow|cow).*")) {                             
                        img = bi.getSubimage(6, 6, 8, 8).getScaledInstance(128, 128, 0);
                    } else if (f.getName().matches("ghast.*")) {
                        img = bi.getSubimage(16, 16, 16, 16).getScaledInstance(128, 128, 0);
                    } else if (f.getName().matches("(cat|ozelot).*")) {
                        img = bi.getSubimage(5, 5, 5, 5).getScaledInstance(128, 128, 0);
                    } else if (f.getName().matches("sheep.*")) {
                        img = bi.getSubimage(8, 8, 6, 6).getScaledInstance(128, 128, 0);
                    } else {                        
                        plugin.logDebug("Mob Default: " + f.getName());
                        img = bi.getSubimage(8, 8, 8, 8).getScaledInstance(128, 128, 0);
                    }
                } else {             
                    BufferedImage combined = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);  
                    Graphics2D g = combined.createGraphics(); 
                    BufferedImage face = bi.getSubimage(8, 8, 8, 8);
                    BufferedImage faceAcc = bi.getSubimage(40, 8, 8, 8);
                    g.drawImage(face, 0, 0, null);
                    g.drawImage(faceAcc, 0, 0, null);
                    img = combined.getScaledInstance(128, 128, 0);
                }
            } 
        } catch (IOException e) {  
            plugin.logError(e.getMessage());
        }        
    }
    
    public void removePlayer(String pName) {
        for (int x = rendered.size()-1; x > 0; x--) {
            if (rendered.get(x).equals(pName)) {
                rendered.remove(x);
            }                
        }
    }
}
