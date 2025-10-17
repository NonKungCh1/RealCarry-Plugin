package com.nonkungch.realcarry;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
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
    private final HashMap<UUID, FallingBlock> carriedBlocks = new HashMap<>();

    private static final List<Material> BLACKLISTED_BLOCKS = Arrays.asList(
            Material.BEDROCK, Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK, Material.BARRIER, Material.SPAWNER,
            Material.END_PORTAL_FRAME, Material.END_PORTAL, Material.NETHER_PORTAL
    );

    public CarryListener(RealCarry plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking() &&
            player.getInventory().getItemInMainHand().getType() == Material.AIR &&
            !carriedBlocks.containsKey(playerUUID) && player.hasPermission("realcarry.carry.block")) {

            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null || BLACKLISTED_BLOCKS.contains(clickedBlock.getType())) {
                player.sendMessage("§cคุณไม่สามารถอุ้มบล็อกนี้ได้!");
                return;
            }

            if (player.getGameMode() == GameMode.SURVIVAL) {
                event.setCancelled(true);
            }

            // --- ส่วนที่แก้ไข ---
            // 1. ดึงข้อมูล BlockData มาก่อน
            BlockData blockData = clickedBlock.getBlockData();
            Location spawnLocation = clickedBlock.getLocation().add(0.5, 0, 0.5);

            // 2. ลบบล็อกจริงออกจากโลกก่อน
            clickedBlock.setType(Material.AIR);

            // 3. สร้าง FallingBlock ด้วยเมธอดที่ถูกต้อง คือ world.spawnFallingBlock
            FallingBlock fallingBlock = player.getWorld().spawnFallingBlock(spawnLocation, blockData);

            // 4. ตั้งค่าอื่นๆ ให้กับ FallingBlock ที่สร้างขึ้นมา
            fallingBlock.setGravity(false);
            fallingBlock.setInvulnerable(true);
            fallingBlock.setDropItem(false);
            // --- จบส่วนที่แก้ไข ---


            fallingBlock.setMetadata("CarriedBlock", new FixedMetadataValue(plugin, player.getUniqueId().toString()));

            player.addPassenger(fallingBlock);
            carriedBlocks.put(playerUUID, fallingBlock);

            player.sendMessage("§aคุณอุ้มบล็อกขึ้นมาแล้ว! กด Shift อีกครั้งเพื่อวาง");
            return;
        }

        if (carriedBlocks.containsKey(playerUUID)) {
            player.sendMessage("§cคุณต้องวางบล็อกลงก่อน!");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (!event.isSneaking() && carriedBlocks.containsKey(playerUUID)) {
            FallingBlock fallingBlock = carriedBlocks.get(playerUUID);
            player.eject();
            Location dropLocation = player.getLocation().getBlock().getLocation();
            dropLocation.getBlock().setBlockData(fallingBlock.getBlockData());
            fallingBlock.remove();
            carriedBlocks.remove(playerUUID);
            player.sendMessage("§aคุณวางบล็อกลงแล้ว!");
        }
    }

    @EventHandler
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock) {
            if (event.getEntity().hasMetadata("CarriedBlock")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (carriedBlocks.containsKey(player.getUniqueId())) {
            dropCarriedBlock(player);
        }
    }

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

    public void dropAllCarriedBlocks() {
        for (UUID playerUUID : carriedBlocks.keySet()) {
            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                dropCarriedBlock(player);
            }
        }
    }
}
