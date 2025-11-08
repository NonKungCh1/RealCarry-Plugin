package com.nonkungch.realcarry;

import com.nonkungch.realcarry.CommandRC; // <--- ตรวจสอบว่า import ถูกต้อง
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class RealCarry extends JavaPlugin {

    private CarryingManager carryingManager;

    @Override
    public void onEnable() {
        // โหลด Config
        saveDefaultConfig();

        // สร้างและส่งต่อ Manager
        this.carryingManager = new CarryingManager(this);
        
        // ลงทะเบียน Events
        getServer().getPluginManager().registerEvents(new CarryListener(this, carryingManager), this);

        // ลงทะเบียน Commands
        getCommand("rc").setExecutor(new CommandRC(this)); // <--- ตรวจสอบว่าใช้ CommandRC

        getLogger().info("RealCarry Plugin (v1.1) ได้เปิดใช้งานแล้ว!");
    }

    @Override
    public void onDisable() {
        // จัดการเคลียร์การอุ้มทั้งหมดเมื่อปิด Plugin
        if (carryingManager != null) {
            carryingManager.clearAllCarrying();
        }
        getLogger().info("RealCarry Plugin ได้ปิดใช้งานแล้ว");
    }

    // สำหรับ /rc reload
    public void reloadPluginConfig() {
        reloadConfig();
    }

    // Helper method สำหรับส่งข้อความ
    public String getMsg(String configPath) {
        String msg = getConfig().getString("messages." + configPath);
        if (msg == null) {
            return ChatColor.RED + "Missing message: " + configPath;
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
