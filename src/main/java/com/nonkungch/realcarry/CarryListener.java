package com.nonkungch.realcarry;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class CarryListener implements Listener {

    private final RealCarry plugin;
    // ใช้เก็บว่าผู้เล่นคนไหนกำลังอุ้มบล็อกอะไรอยู่
    private final HashMap<UUID, FallingBlock> carriedBlocks = new HashMap<>();

    // รายชื่อบล็อกที่ไม่อนุญาตให้อุ้ม
    private static final List<Material> BLACKLISTED_BLOCKS = Arrays.asList(
            Material.BEDROCK, Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK, Material.BARRIER, Material.SPAWNER,
            Material.END_PORTAL_FRAME, Material.END_PORTAL, Material.NETHER_PORTAL
    );

    public CarryListener(RealCarry plugin) {
        this.plugin = plugin;
    }

    // --- Logic การอุ้มและวางบล็อก ---
    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // 1. Logic การอุ้มบล็อก
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking() &&
            player.getInventory().getItemInMainHand().getType() == Material.AIR &&
            !carriedBlocks.containsKey(playerUUID) && player.hasPermission("realcarry.carry.block")) {

            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null || BLACKLISTED_BLOCKS.contains(clickedBlock.getType())) {
                player.sendMessage("§cคุณไม่สามารถอุ้มบล็อกนี้ได้!");
                return;
            }

            // ถ้าเป็น Survival Mode จะยกเลิกไม่ให้เปิด Chest
            if (player.getGameMode() == GameMode.SURVIVAL) {
                event.setCancelled(true);
            }

            // สร้าง FallingBlock ขึ้นมาแทนที่บล็อกจริง
            Location blockLocation = clickedBlock.getLocation().add(0.5, 0, 0.5);
            FallingBlock fallingBlock = player.getWorld().spawn(blockLocation, FallingBlock.class, (fb) -> {
                fb.setBlockData(clickedBlock.getBlockData());
                fb.setGravity(false); // ไม่ให้บล็อกร่วง
                fb.setInvulnerable(true); // ทำให้บล็อกไม่ถูกทำลาย
                fb.setDropItem(false); // ไม่ให้ดรอปเป็นไอเทม
            });

            // เพิ่ม Metadata เพื่อให้รู้ว่าบล็อกนี้เป็นของเรา
            fallingBlock.setMetadata("CarriedBlock", new FixedMetadataValue(plugin, player.getUniqueId().toString()));

            // เอาบล็อกไปขี่บนหัวผู้เล่น
            player.addPassenger(fallingBlock);
            carriedBlocks.put(playerUUID, fallingBlock);

            // ลบบล็อกออกจากโลก
            clickedBlock.setType(Material.AIR);
            player.sendMessage("§aคุณอุ้มบล็อกขึ้นมาแล้ว! กด Shift อีกครั้งเพื่อวาง");
            return;
        }

        // 2. ป้องกันการกระทำระหว่างอุ้ม
        if (carriedBlocks.containsKey(playerUUID)) {
            player.sendMessage("§cคุณต้องวางบล็อกลงก่อน!");
            event.setCancelled(true);
        }
    }

    // --- Logic การวางบล็อกด้วยการกด Shift ---
    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // เช็คว่าผู้เล่นกำลังจะ "เลิกย่อ" และกำลังอุ้มบล็อกอยู่
        if (!event.isSneaking() && carriedBlocks.containsKey(playerUUID)) {
            FallingBlock fallingBlock = carriedBlocks.get(playerUUID);

            // วางบล็อกลงที่ตำแหน่งของผู้เล่น
            player.eject(); // ปล่อยบล็อกออกจากตัว
            Location dropLocation = player.getLocation().getBlock().getLocation();
            dropLocation.getBlock().setBlockData(fallingBlock.getBlockData());

            fallingBlock.remove(); // ลบ FallingBlock ทิ้ง
            carriedBlocks.remove(playerUUID);
            player.sendMessage("§aคุณวางบล็อกลงแล้ว!");
        }
    }

    // --- ป้องกันไม่ให้ FallingBlock ของเรากลายเป็นบล็อกเอง ---
    @EventHandler
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock) {
            // เช็คจาก Metadata ว่าเป็นบล็อกที่เราอุ้มอยู่หรือไม่
            if (event.getEntity().hasMetadata("CarriedBlock")) {
                event.setCancelled(true);
            }
        }
    }

    // --- จัดการกรณีผู้เล่นออกจากเกม ---
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (carriedBlocks.containsKey(player.getUniqueId())) {
            dropCarriedBlock(player);
        }
    }

    // --- ฟังก์ชันช่วยสำหรับวางบล็อก (ใช้ในหลายที่) ---
    private void dropCarriedBlock(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (carriedBlocks.containsKey(playerUUID)) {
            FallingBlock fallingBlock = carriedBlocks.get(playerUUID);
            player.eject();
            Location dropLocation = player.getLocation().getBlock().getLocation();
            dropLocation.getBlock().setBlockData(fallingBlock.getBlockData());
            fallingBlock.remove();
            carriedBlocks.remove(playerUUID);
        }
    }

    // --- ฟังก์ชันสำหรับตอนปิดปลั๊กอิน ---
    public void dropAllCarriedBlocks() {
        for (UUID playerUUID : carriedBlocks.keySet()) {
            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                dropCarriedBlock(player);
            }
        }
    }
}
