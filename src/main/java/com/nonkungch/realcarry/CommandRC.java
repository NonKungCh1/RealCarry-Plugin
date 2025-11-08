package com.nonkungch.realcarry;

import com.nonkungch.realcarry.RealCarry;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandRC implements CommandExecutor {

    private final RealCarry plugin;

    public CommandRC(RealCarry plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (args.length == 0) {
            // /rc (แสดง Help)
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            // /rc reload
            if (!sender.hasPermission("realcarry.reload")) {
                sender.sendMessage(plugin.getMsg("no-permission"));
                return true;
            }
            
            plugin.reloadPluginConfig();
            sender.sendMessage(plugin.getMsg("plugin-reloaded"));
            return true;
        }

        // ถ้าพิมพ์คำสั่งผิด
        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- [RealCarry Help] ---");
        sender.sendMessage(ChatColor.AQUA + "/rc help " + ChatColor.GRAY + "- แสดงข้อความนี้");
        sender.sendMessage(ChatColor.AQUA + "/rc reload " + ChatColor.GRAY + "- รีโหลด config");
        sender.sendMessage(ChatColor.GREEN + "วิธีใช้: " + ChatColor.WHITE + "Shift+คลิกขวา ที่สัตว์/บล็อก เพื่ออุ้ม");
        sender.sendMessage(ChatColor.GREEN + "วิธีวาง: " + ChatColor.WHITE + "Shift+คลิกขวา ที่พื้นอีกครั้ง");
    }
}
