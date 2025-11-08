package com.nonkungch.realcarry;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.UUID;

public class CarryingManager {

    private final RealCarry plugin;
    private final HashMap<UUID, Entity> carryingEntity = new HashMap<>();
    private final HashMap<UUID, BlockData> carryingBlock = new HashMap<>();
    private final HashMap<UUID, ArmorStand> blockVisual = new HashMap<>();

    public CarryingManager(RealCarry plugin) {
        this.plugin = plugin;
    }

    public boolean isCarrying(Player player) {
        UUID uuid = player.getUniqueId();
        return carryingEntity.containsKey(uuid) || carryingBlock.containsKey(uuid);
    }

    // --- การอุ้มสัตว์ (Entity) ---
    public void startCarryingEntity(Player player, Entity entity) {
        player.addPassenger(entity);
        carryingEntity.put(player.getUniqueId(), entity);
        applySlowEffect(player);
        player.sendMessage(plugin.getMsg("carrying-entity").replace("%entity%", entity.getName()));
    }

    // --- การอุ้มบล็อก (Block) ---
    public void startCarryingBlock(Player player, Block block) {
        BlockData data = block.getBlockData();
        Material type = block.getType();
        
        // เก็บข้อมูล
        carryingBlock.put(player.getUniqueId(), data);
        
        // ลบบล็อกออกจากโลก
        block.setType(Material.AIR);

        // สร้างตัวแสดงผล (Armor Stand)
        ArmorStand armorStand = (ArmorStand) player.getWorld().spawnEntity(player.getLocation(), EntityType.ARMOR_STAND);
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setInvulnerable(true);
        armorStand.setMarker(true); // ป้องกันการชน
        
        // ทำให้ Armor Stand "สวม" บล็อกนั้น
        armorStand.getEquipment().setHelmet(new ItemStack(type));
        armorStand.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.READ_ONLY);

        // ให้ Armor Stand ขี่ผู้เล่น
        player.addPassenger(armorStand);
        blockVisual.put(player.getUniqueId(), armorStand);

        applySlowEffect(player);
        player.sendMessage(plugin.getMsg("carrying-block").replace("%block%", type.name()));
    }

    // --- การวาง ---
    public void stopCarrying(Player player, Location dropLocation) {
        UUID uuid = player.getUniqueId();
        
        if (carryingEntity.containsKey(uuid)) {
            // วางสัตว์
            Entity entity = carryingEntity.remove(uuid);
            player.removePassenger(entity);
            entity.teleport(dropLocation);
            
        } else if (carryingBlock.containsKey(uuid)) {
            // วางบล็อก
            BlockData data = carryingBlock.remove(uuid);
            ArmorStand armorStand = blockVisual.remove(uuid);

            // ลบตัวแสดงผล
            if (armorStand != null) {
                player.removePassenger(armorStand);
                armorStand.remove();
            }

            // วางบล็อกกลับคืน
            dropLocation.getBlock().setBlockData(data);
        }

        removeSlowEffect(player);
        player.sendMessage(plugin.getMsg("placed-object"));
    }

    // --- Slowness Effect ---
    private void applySlowEffect(Player player) {
        int level = plugin.getConfig().getInt("slowness-level", 0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, level, true, false));
    }

    private void removeSlowEffect(Player player) {
        player.removePotionEffect(PotionEffectType.SLOW);
    }
    
    // --- Cleanup ---
    public void handlePlayerQuit(Player player) {
        if (isCarrying(player)) {
            // บังคับวางของที่ตำแหน่งผู้เล่น
            stopCarrying(player, player.getLocation());
        }
    }

    public void clearAllCarrying() {
        // ใช้สำหรับ onDisable เพื่อป้องกันบัค
        for (UUID uuid : carryingEntity.keySet()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) stopCarrying(p, p.getLocation());
        }
        for (UUID uuid : carryingBlock.keySet()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) stopCarrying(p, p.getLocation());
        }
    }
}
