package com.nonkungch.realcarry;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CarryCommand implements CommandExecutor {

    private final RealCarry plugin;

    public CarryCommand(RealCarry plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            // ตรวจสอบ Permission สำหรับ /realcarry reload
            if (!sender.hasPermission("realcarry.reload")) {
                sender.sendMessage(plugin.getMessage("no-permission"));
                return true;
            }

            // ทำการ Reload Config และไฟล์ภาษา
            plugin.reloadConfig();
            plugin.loadLang(); // โหลดภาษาใหม่หลังจาก reload config
            sender.sendMessage(plugin.getMessage("reload-success"));
            return true;
        }

        // ถ้าพิมพ์ /realcarry เฉยๆ หรือคำสั่งผิด
        sender.sendMessage(plugin.getMessage("unknown-command"));
        return true;
    }
}
