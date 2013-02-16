/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package me.cnaude.plugin.HeadMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

/**
 *
 * @author cnaude
 */
public class HMMain extends JavaPlugin implements Listener {

    public static String LOG_HEADER;
    public static final int MAGIC_NUMBER = Integer.MAX_VALUE - 395742;
    public static final int MAX_ID = Short.MAX_VALUE;
    public static final int MIN_ID = 1;
    public static final String DEFAULT_SKIN = "HeadMapDefault";
    static final Logger log = Logger.getLogger("Minecraft");
    private File pluginFolder;
    private File imagesFolder;
    private File cacheFolder;
    private File configFile;
    private File mapsFile;
    private static boolean debugEnabled = false;
    private static HashMap<Short, String> mapIdList = new HashMap<Short, String>();
    private static HashMap<Short, String> mapTypeList = new HashMap<Short, String>();

    @Override
    public void onEnable() {
        LOG_HEADER = "[" + this.getName() + "]";
        pluginFolder = getDataFolder();
        cacheFolder = new File(pluginFolder.getAbsolutePath() + "/cache");
        imagesFolder = new File(pluginFolder.getAbsolutePath() + "/images");
        mapsFile = new File(pluginFolder.getAbsolutePath() + "/maps.txt");
        configFile = new File(pluginFolder, "config.yml");
        createConfig();
        this.getConfig().options().copyDefaults(true);
        saveConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);

        ShapelessRecipe shapelessRecipe = new ShapelessRecipe(new ItemStack(Material.MAP, 1));
        shapelessRecipe.addIngredient(1, Material.SKULL_ITEM, -1);
        shapelessRecipe.addIngredient(1, Material.MAP, -1);
        getServer().addRecipe(shapelessRecipe);

        createDefaultSkin();
        loadMapIdList();
        getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            @Override
            public void run() {                
                postWorldLoad();
            }
        }, 0);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                saveMapIdList();
            }
        }, 2400, 2400 );
    }

    @Override
    public void onDisable() {
        saveMapIdList();
    }

    @EventHandler
    public void onPrepareItemCraftEvent(PrepareItemCraftEvent event) {        
        if (event.getRecipe() instanceof Recipe) {
            CraftingInventory ci = event.getInventory();
            ItemStack result = ci.getResult();
            if (result.getType().equals(Material.MAP)) {
                for (ItemStack i : ci.getContents()) {
                    if (i.getType().equals(Material.SKULL_ITEM)) {
                        if (i.getData().getData() != (byte) 3) {
                            ci.setResult(new ItemStack(0));
                            return;
                        }                        
                    }
                }
                for (ItemStack i : ci.getContents()) {
                    if (i.hasItemMeta() && i.getType().equals(Material.SKULL_ITEM)) {
                        ItemMeta im = i.getItemMeta();
                        SkullMeta sm = ((SkullMeta) im);
                        if (sm.hasOwner()) {
                            ItemStack res = getMap(null, sm.getOwner(),"face");
                            if (res != null) {
                                if (res.getDurability() > 0) {
                                    ci.setResult(res);
                                    break;
                                } 
                            } 
                            ci.setResult(new ItemStack(0));                                                                                                                   
                        } else {
                            
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (player.hasPermission("headmap.create")) {
                if (args.length >= 1) {
                    String type = "face";
                    String name = args[0];
                    
                    if (args.length > 1) {
                        type = args[1];
                    }
                    ItemStack result;
                    if (name.toLowerCase().endsWith(".png") 
                            || name.toLowerCase().endsWith(".jpg")
                            || name.toLowerCase().endsWith(".gif")) {
                        type = "image";
                    } 
                    result = getMap(player, name, type);
                    if (!result.getType().equals(Material.EMPTY_MAP)) {
                        Location loc = player.getLocation().clone();
                        World world = loc.getWorld();
                        world.dropItemNaturally(loc, result);    
                    }
                    return true;
                } else {
                    return false;
                }
            } else {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            }
        } else {
            sender.sendMessage("Only a player can use this command!");
        }
        return true;
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        final String pName = event.getPlayer().getName();
        getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                downloadSkin(pName);
            }
        });
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        cleanup(event.getPlayer().getName());
    }

    public boolean downloadSkin(String pName) {
        try {
            URL website = new URL("http://skins.minecraft.net/MinecraftSkins/" + pName + ".png");
            logDebug("Attempting to download player skin: " + website.toString());
            ReadableByteChannel rbc = Channels.newChannel(website.openStream());
            FileOutputStream fos = new FileOutputStream(getFileName(pName,"face"));
            fos.getChannel().transferFrom(rbc, 0, 1 << 24);
            return true;
        } catch (Exception e) {
            logError(e.getMessage());
            return false;
        }
    }

    public String getFileName(String name, String type) {
        if (type.equals("image")) {
            return imagesFolder.getAbsolutePath() + "/" + name;            
        } else {
            return cacheFolder.getAbsolutePath() + "/" + name + ".png";
        } 
    }

    public void postWorldLoad() {
        for (short mapId : mapIdList.keySet()) {
            String name = mapIdList.get(mapId);
            String type = mapTypeList.get(mapId);   
            String fileName = getFileName(name, type); 
            MapView mv = getServer().getMap(mapId);
            PictureRenderer pr = new PictureRenderer(fileName, this, type);
            for (MapRenderer mr : mv.getRenderers()) {
                mv.removeRenderer(mr);
            }
            mv.addRenderer(pr);
            logDebug("Loaded to mapIdList: " + mapId + " => " + name + " => " + type);
        }
        logInfo("Maps loaded: " + mapIdList.size());
    }
    
    @EventHandler
    public void onItemDespawnEvent(ItemDespawnEvent event) {
        if(event.isCancelled()) {
            return;
        }
        ItemStack item = event.getEntity().getItemStack();
        if (item.getType().equals(Material.MAP)) {            
            cleanLists(item.getDurability());            
        }
    }
    
    @EventHandler
    public void onEntityCombustEvent(EntityCombustEvent event) {
        if(event.isCancelled()) {
            return;
        }
        if (event.getEntity().getType().equals(EntityType.DROPPED_ITEM)) {
            ItemStack item = ((Item) event.getEntity()).getItemStack(); 
            if (item.getType().equals(Material.MAP)) {
                cleanLists(item.getDurability());    
            }
        }
    }
    
    private void cleanLists(short mapId) {
        logDebug("Removing burned map: " + mapId);
        if (mapIdList.containsKey(mapId)) {                
                mapIdList.remove(mapId);
        }
        if (mapTypeList.containsKey(mapId)) {            
            mapTypeList.remove(mapId);
        }
    }

    public void cleanup(String pName) {
        for (short mapId : mapIdList.keySet()) {
            MapView mv = getServer().getMap(mapId);
            for (MapRenderer mr : mv.getRenderers()) {
                ((PictureRenderer) mr).removePlayer(pName);
                logDebug("Removing player " + pName + " from map " + mapId);
            }
        }
    }

    public void loadMapIdList() {
        BufferedReader reader = null;
        if (mapsFile.exists()) {
            try {
                reader = new BufferedReader(new FileReader(mapsFile));
                String text;
                while ((text = reader.readLine()) != null) {
                    logDebug("Read from file: " + text);
                    String[] items = text.split(":", 3);
                    mapIdList.put(Short.parseShort(items[0]), items[1]);                    
                    if (items.length == 3) {
                        mapTypeList.put(Short.parseShort(items[0]), items[2]);
                    } else {
                        mapTypeList.put(Short.parseShort(items[0]), "face");
                    }
                }
            } catch (IOException e) {
                logError(e.getMessage());
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    logError(e.getMessage());
                }
            }
        }
    }

    public void createDefaultSkin() {
        File file = new File(cacheFolder.getAbsolutePath() + "/" + DEFAULT_SKIN + ".png");
        if (!file.exists()) {            
            try {
                InputStream in = HMMain.class.getResourceAsStream("/me/cnaude/plugin/HeadMap/skin/char.png");
                byte[] buf = new byte[1024];
                int len;
                OutputStream out = new FileOutputStream(file);
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.close();
            } catch (Exception ex) {
                logError(ex.getMessage());
            }
        }
    }
    
    public void saveMapIdList() {
        try {
            PrintWriter out = new PrintWriter(mapsFile);
            for (short mapId : mapIdList.keySet()) {
                out.println(mapId + ":" + mapIdList.get(mapId) + ":" + mapTypeList.get(mapId));
                logDebug("Saved to " + mapsFile.getName() + ": " + mapId + " => " + mapIdList.get(mapId));
            }
            out.close();
            logInfo("Maps saved: " + mapIdList.size());
        } catch (Exception ex) {
            logError(ex.getMessage());
        }
    }

    public ItemStack getMap(Player player, String name, String type) {
        ItemStack m = new ItemStack(Material.EMPTY_MAP);        
        String fileName;
        fileName = getFileName(name,type);        
        File f = new File(fileName);
        logDebug("getMap(" + name + "," + type + ")");
        if (!f.exists() && !type.equals("image")) {
            downloadSkin(name);
        } 
        if (!f.exists() && !type.equals("image")) {
            name = getFileName(DEFAULT_SKIN,type);
        } 
        if (f.exists()) {
            m = new ItemStack(Material.MAP);    
            MapView mv = getServer().createMap(getServer().getWorlds().get(0));
            mv.setCenterX(MAGIC_NUMBER);
            mv.setCenterZ(0);
            for (MapRenderer mr : mv.getRenderers()) {
                mv.removeRenderer(mr);
            }            
            mv.addRenderer(new PictureRenderer(fileName, this, type));
            ItemMeta im = m.getItemMeta();
            im.setDisplayName(ChatColor.GREEN + name);
            m.setItemMeta(im);
            m.setDurability(mv.getId());
            mapIdList.put(mv.getId(), name);
            mapTypeList.put(mv.getId(), type);
            logDebug("Added to mapIdList: " + mv.getId() + " => " + name);
        } else {
            if (player != null) {
                player.sendMessage(ChatColor.RED + "Unable to load image: " + f.getName());
            }
        } 
        return m;
    }

    private void createConfig() {
        if (!pluginFolder.exists()) {
            try {
                pluginFolder.mkdir();
            } catch (Exception e) {
                logError(e.getMessage());
            }
        }
        if (!cacheFolder.exists()) {
            try {
                cacheFolder.mkdir();
            } catch (Exception e) {
                logError(e.getMessage());
            }
        }
        
        if (!imagesFolder.exists()) {
            try {
                imagesFolder.mkdir();
            } catch (Exception e) {
                logError(e.getMessage());
            }
        }

        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (Exception e) {
                logError(e.getMessage());
            }
        }
    }

    private void loadConfig() {
        debugEnabled = getConfig().getBoolean("debug-enabled");
        logDebug("Debug enabled");


    }

    public void logInfo(String _message) {
        log.log(Level.INFO, String.format("%s %s", LOG_HEADER, _message));
    }

    public void logError(String _message) {
        log.log(Level.SEVERE, String.format("%s %s", LOG_HEADER, _message));
    }

    public void logDebug(String _message) {
        if (debugEnabled) {
            log.log(Level.INFO, String.format("%s [DEBUG] %s", LOG_HEADER, _message));
        }
    }
}
