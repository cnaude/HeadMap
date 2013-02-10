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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
    static final Logger log = Logger.getLogger("Minecraft");
    private File pluginFolder;
    private File cacheFolder;
    private File configFile;
    private File mapsFile;
    private static boolean debugEnabled = false;
    private static HashMap<Short, String> mapIdList = new HashMap<Short, String>();

    @Override
    public void onEnable() {
        LOG_HEADER = "[" + this.getName() + "]";
        pluginFolder = getDataFolder();
        cacheFolder = new File(pluginFolder.getAbsolutePath() + "/cache");
        mapsFile = new File(pluginFolder.getAbsolutePath() + "/maps.txt");
        configFile = new File(pluginFolder, "config.yml");
        createConfig();
        this.getConfig().options().copyDefaults(true);
        saveConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);

        ItemStack result = new ItemStack(Material.MAP, 1);
        ShapelessRecipe shapelessRecipe = new ShapelessRecipe(result);
        shapelessRecipe.addIngredient(1, Material.SKULL_ITEM, -1);
        shapelessRecipe.addIngredient(1, Material.MAP, -1);
        getServer().addRecipe(shapelessRecipe);

        getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            @Override
            public void run() {
                loadMapIdList();
                postWorldLoad();
            }
        }, 0);
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
                            ItemStack res = getPicture(sm.getOwner());
                            ci.setResult(res);
                            break;
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
                    if (downloadSkin(args[0])) {
                        ItemStack result = getPicture(args[0]);
                        Location loc = player.getLocation().clone();
                        World world = loc.getWorld();
                        world.dropItemNaturally(loc, result);
                    }
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
            FileOutputStream fos = new FileOutputStream(getPlayerSkin(pName));
            fos.getChannel().transferFrom(rbc, 0, 1 << 24);
            return true;
        } catch (Exception e) {
            logError(e.getMessage());
            return false;
        }
    }

    public String getPlayerSkin(String pName) {
        return cacheFolder.getAbsolutePath() + "/" + pName + ".png";
    }

    public void postWorldLoad() {
        for (short mapId : mapIdList.keySet()) {
            String pName = mapIdList.get(mapId);
            MapView mv = getServer().getMap(mapId);
            String fileName = getPlayerSkin(pName);            
            PictureRenderer pr = new PictureRenderer(fileName, this);
            for (MapRenderer mr : mv.getRenderers()) {
                mv.removeRenderer(mr);
            }
            mv.addRenderer(pr);
            logDebug("Loaded to mapIdList: " + mv.getId() + " => " + pName);
        }
        logInfo("Maps loaded: " + mapIdList.size());
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
        try {
            reader = new BufferedReader(new FileReader(mapsFile));
            String text;
            while ((text = reader.readLine()) != null) {
                logDebug("Read from file: " + text);
                String[] items = text.split(":", 2);
                mapIdList.put(Short.parseShort(items[0]), items[1]);
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

    public void saveMapIdList() {
        try {
            PrintWriter out = new PrintWriter(mapsFile);
            for (short mapId : mapIdList.keySet()) {
                out.println(mapId + ":" + mapIdList.get(mapId));
                logDebug("Saved to " + mapsFile.getName() + ": " + mapId + " => " + mapIdList.get(mapId));
            }
            out.close();
            logInfo("Maps saved: " + mapIdList.size());
        } catch (Exception ex) {
            logError(ex.getMessage());
        }
    }

    public ItemStack getPicture(String pName) {
        ItemStack m = new ItemStack(Material.MAP);        
        String fileName = getPlayerSkin(pName);
        File f = new File(fileName);
        if (!f.exists()) {
            downloadSkin(pName);
        }
        if (f.exists()) {
            MapView mv = getServer().createMap(getServer().getWorlds().get(0));
            mv.setCenterX(MAGIC_NUMBER);
            mv.setCenterZ(0);
            for (MapRenderer mr : mv.getRenderers()) {
                mv.removeRenderer(mr);
            }            
            mv.addRenderer(new PictureRenderer(fileName, this));
            ItemMeta im = m.getItemMeta();
            im.setDisplayName(ChatColor.GREEN + pName);
            m.setItemMeta(im);
            m.setDurability(mv.getId());
            mapIdList.put(mv.getId(), pName);
            logDebug("Added to mapIdList: " + mv.getId() + " => " + pName);
        } else {
            logError("Unable to load skin file: " + f.getName());
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