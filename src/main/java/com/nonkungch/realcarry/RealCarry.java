package com.nonkungch.realcarry;

import org.bukkit.plugin.java.JavaPlugin;

public final class RealCarry extends JavaPlugin {

    private CarryListener carryListener;

    @Override
    public void onEnable() {
        getLogger().info("RealCarry Plugin has been enabled!");
        this.carryListener = new CarryListener(this);
        getServer().getPluginManager().registerEvents(this.carryListener, this);
    }

    @Override
    public void onDisable() {
        // เมื่อปลั๊กอินปิด ควรจะดรอปบล็อกที่ผู้เล่นทุกคนกำลังอุ้มอยู่
        if (carryListener != null) {
            carryListener.dropAllCarriedBlocks();
        }
        getLogger().info("RealCarry Plugin has been disabled.");
    }
}
