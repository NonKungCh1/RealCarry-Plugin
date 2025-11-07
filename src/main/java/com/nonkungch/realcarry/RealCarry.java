package com.nonkungch.realcarry;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class RealCarry extends JavaPlugin {

    private CarryListener carryListener;
    private FileConfiguration langConfig = null;
    private File langFile = null;

    @Override
    public void onEnable() {
        getLogger().info("RealCarry Plugin has been enabled!");

        saveDefaultConfig(); 
        loadLang();

        this.carryListener = new CarryListener(this); 
        getServer().getPluginManager().registerEvents(this.carryListener, this);

        getCommand("realcarry").setExecutor(new CarryCommand(this));

        this.carryListener.startCarryTask();
    }

    @Override
    public void onDisable() {
        if (carryListener != null) {
            carryListener.stopCarryTask();
            carryListener.dropAllCarriedThings();
        }
        getLogger().info("RealCarry Plugin has been disabled.");
    }

    /**
     * ตรวจสอบว่าผู้เล่นกำลังอุ้มอะไรอยู่หรือไม่ (สำหรับ Addon)
     */
    public boolean isPlayerCarrying(Player player) {
        if (carryListener != null) {
            // --- [แก้ไข บรรทัดที่ 60] ---
            return carryListener.isPlayerCarrying(player);
            // -------------------------
        }
        return false;
    }

    /**
     * ดึง Entity ที่ผู้เล่นกำลังอุ้มอยู่ (สำหรับ Addon)
     */
    public Entity getCarriedEntity(Player player) {
        if (carryListener != null) {
            // --- [แก้ไข บรรทัดที่ 72] ---
            return carryListener.getCarriedEntity(player);
            // -------------------------
        }
        return null;
    }

    /**
     * API method to force a player to drop what they are carrying.
     */
    public void forceDrop(Player player, Location dropLocation) {
        if (carryListener != null && isPlayerCarrying(player)) {
            carryListener.dropCarriedThing(player, dropLocation, false); 
        }
    }

    // --- (โค้ดส่วนที่เหลือของคุณ ไม่ต้องแก้) ---
    public String getMessage(String path) {
        String message = getLangConfig().getString(path);
        if (message == null) {
            return ChatColor.RED + "Message not found: " + path;
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public FileConfiguration getLangConfig() {
        if (langConfig == null) {
            loadLang();
        }
        return this.langConfig;
    }

    public void loadLang() {
        File langDir = new File(getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        saveResource("lang/en.yml", false);
        saveResource("lang/th.yml", false);
        String langFileName = getConfig().getString("language", "en") + ".yml";
        langFile = new File(langDir, langFileName);
        if (!langFile.exists()) {
            getLogger().warning("Language file " + langFileName + " not found! Defaulting to en.yml.");
            langFile = new File(langDir, "en.yml");
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        try (InputStream defaultStream = getResource("lang/" + langFile.getName())) {
            if (defaultStream != null) {
                langConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        getLogger().info("Loaded language file: " + langFile.getName());
    }
}
