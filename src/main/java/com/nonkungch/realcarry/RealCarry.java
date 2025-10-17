package com.nonkungch.realcarry;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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

        // บันทึกและโหลดไฟล์ config.yml และไฟล์ภาษา
        saveDefaultConfig();
        loadLang();

        this.carryListener = new CarryListener(this);
        getServer().getPluginManager().registerEvents(this.carryListener, this);
    }

    @Override
    public void onDisable() {
        if (carryListener != null) {
            carryListener.dropAllCarriedThings();
        }
        getLogger().info("RealCarry Plugin has been disabled.");
    }

    /**
     * ดึงข้อความจากไฟล์ภาษาที่โหลดไว้
     * @param path คือ key ของข้อความ (เช่น 'cannot-carry')
     * @return ข้อความที่แปลงสีแล้ว
     */
    public String getMessage(String path) {
        String message = getLangConfig().getString(path);
        if (message == null) {
            return ChatColor.RED + "Message not found: " + path;
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * เมธอดสำหรับให้คลาสอื่นเข้าถึงไฟล์ภาษาที่โหลดไว้
     * @return FileConfiguration ของไฟล์ภาษา
     */
    public FileConfiguration getLangConfig() {
        if (langConfig == null) {
            loadLang(); // โหลดใหม่ถ้ายังไม่มี
        }
        return this.langConfig;
    }

    /**
     * โหลดไฟล์ภาษาที่ระบุใน config.yml
     */
    public void loadLang() {
        // สร้างโฟลเดอร์ lang
        File langDir = new File(getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        // คัดลอกไฟล์ภาษาเริ่มต้น (en.yml, th.yml) ถ้ายังไม่มี
        saveResource("lang/en.yml", false);
        saveResource("lang/th.yml", false);

        // หาไฟล์ภาษาจาก config
        String langFileName = getConfig().getString("language", "en") + ".yml";
        langFile = new File(langDir, langFileName);

        if (!langFile.exists()) {
            getLogger().warning("Language file " + langFileName + " not found! Defaulting to en.yml.");
            langFile = new File(langDir, "en.yml");
        }
        
        // โหลดไฟล์ภาษาเข้ามาใน langConfig พร้อมรองรับ UTF-8
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
