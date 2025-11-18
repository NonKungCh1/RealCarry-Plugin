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
    // Map สำหรับเก็บ Entity ที่ผู้เล่นกำลังอุ้ม (Key: Player UUID)
    private final HashMap<UUID, Entity> carryingEntity = new HashMap<>();
    // Map สำหรับเก็บ BlockData ของบล็อกที่ผู้เล่นกำลังอุ้ม (Key: Player UUID)
    private final HashMap<UUID, BlockData> carryingBlock = new HashMap<>();
    // Map สำหรับเก็บ ArmorStand ที่แสดงผลบล็อก (Key: Player UUID)
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
        // ปรับตำแหน่งการเกิดของ ArmorStand ให้สูงขึ้นเล็กน้อย (0.01)
        Location spawnLoc = player.getLocation().add(0, 0.01, 0); 
        ArmorStand armorStand = (ArmorStand) player.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setInvulnerable(true);
        // armorStand.setMarker(true); // ป้องกันบั๊กการลบในบางกรณี
        
        // ทำให้ Armor Stand "สวม" บล็อกนั้น
        armorStand.getEquipment().setHelmet(new ItemStack(type));
        
        // ล็อกไม่ให้ผู้เล่นถอดหมวก
        armorStand.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);

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
            
            // ลบ Entity ออกจาก Passenger
            if (entity != null && player.getPassengers().contains(entity)) {
                 player.removePassenger(entity);
            }
            
            // วาง Entity
            if (entity != null && entity.isValid()) {
                // วางบนพื้นดินตรงกลางบล็อก (Y ปรับให้เป็น Y ของบล็อกที่คลิก + 0)
                Location finalDropLoc = dropLocation.clone();
                finalDropLoc.setY(dropLocation.getBlockY()); 
                finalDropLoc.add(0.5, 0, 0.5);
                entity.teleport(finalDropLoc);
            }
            
        } else if (carryingBlock.containsKey(uuid)) {
            // วางบล็อก
            BlockData data = carryingBlock.remove(uuid);
            ArmorStand armorStand = blockVisual.remove(uuid);

            // ลบตัวแสดงผล (Armor Stand)
            if (armorStand != null) {
                // ลบออกจาก Passenger
                if (player.getPassengers().contains(armorStand)) {
                    player.removePassenger(armorStand);
                }
                
                // ลบ Armor Stand ออกจากโลกอย่างสมบูรณ์ (สำคัญมากสำหรับบั๊กหลายผู้เล่น)
                if (armorStand.isValid()) {
                    armorStand.remove();
                }
            }

            // วางบล็อกกลับคืน
            if (data != null) {
                // วางที่บล็อกที่คลิกขวา (dropLocation คือตำแหน่งบล็อกถัดไป)
                dropLocation.getBlock().setBlockData(data);
            }
        }

        removeSlowEffect(player);
        player.sendMessage(plugin.getMsg("placed-object"));
    }

    // --- Slowness Effect ---
    private void applySlowEffect(Player player) {
        int level = plugin.getConfig().getInt("slowness-level", 0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, level, true, false));
    }

    private void removeSlowEffect(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }
    
    // --- Cleanup ---
    public void handlePlayerQuit(Player player) {
        if (isCarrying(player)) {
            // บังคับวางของที่ตำแหน่งผู้เล่น (เพิ่ม 0.5 ให้ไม่จมดิน)
            stopCarrying(player, player.getLocation().add(0, 0.5, 0)); 
        }
    }

    public void clearAllCarrying() {
        // ใช้สำหรับ onDisable เพื่อป้องกันบัค
        // วนลูปและวางของทั้งหมด
        
        // วนลูป Entity
        for (UUID uuid : new HashMap<>(carryingEntity).keySet()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) stopCarrying(p, p.getLocation().add(0, 0.5, 0)); 
        }
        
        // วนลูป Block
        for (UUID uuid : new HashMap<>(carryingBlock).keySet()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) stopCarrying(p, p.getLocation().add(0, 0.5, 0)); 
        }
    }
}
