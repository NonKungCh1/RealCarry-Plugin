package com.nonkungch.realcarry;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot; // สำหรับตรวจสอบมือหลัก/รอง
import org.bukkit.inventory.ItemStack; // สำหรับตรวจสอบมือเปล่า

import java.util.HashMap;
import java.util.UUID;

public class CarryListener implements Listener {

    private final CarryingManager manager;

    private final HashMap<UUID, Long> cooldown = new HashMap<>();

    public CarryListener(RealCarry plugin, CarryingManager manager) {
        this.manager = manager;
    }

    private boolean onCooldown(Player p) {
        long now = System.currentTimeMillis();
        long last = cooldown.getOrDefault(p.getUniqueId(), 0L);
        return now - last < 200;
    }

    private void setCooldown(Player p) {
        cooldown.put(p.getUniqueId(), System.currentTimeMillis());
    }

    // ฟังก์ชันตรวจสอบว่าผู้เล่น 'มือเปล่า' (มือหลัก) หรือไม่
    private boolean isHandEmpty(Player player) {
        // ตรวจสอบมือหลัก (Main Hand) ว่าเป็นอากาศ (มือเปล่า) หรือไม่
        ItemStack item = player.getInventory().getItemInMainHand();
        return item == null || item.getType() == Material.AIR;
    }

    // ===============================================================
    //                อุ้ม Player / อุ้มสัตว์ / อุ้มมอนทุกชนิด
    // ===============================================================
    @EventHandler
    public void onEntityInteract(PlayerInteractAtEntityEvent event) {

        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        // 1. ตรวจสอบว่าผู้เล่นกำลังอุ้มอยู่หรือไม่ (ถ้าอุ้มอยู่แล้ว ให้ยกเลิกการกระทำนี้)
        if (manager.isCarrying(player)) {
            event.setCancelled(true);
            return;
        }

        if (!player.isSneaking()) return;
        if (onCooldown(player)) return;
        
        // 2. ตรวจสอบว่าผู้เล่น 'มือเปล่า' หรือไม่ (ถ้าไม่มือเปล่า ยกเลิก)
        if (!isHandEmpty(player)) return;

        setCooldown(player);

        // อุ้มผู้เล่น + อุ้มมอนทุกชนิดที่ "มีชีวิต"
        if (entity.getType().isAlive()) {
            manager.startCarryingEntity(player, entity);
            event.setCancelled(true);
        }
    }

    // ===============================================================
    //                       อุ้มบล็อก / วางบล็อก
    // ===============================================================
    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {

        Player player = event.getPlayer();

        if (!player.isSneaking()) return;
        if (onCooldown(player)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        // ตรวจสอบว่าคลิกด้วยมือหลัก (Main Hand) เท่านั้น
        if (event.getHand() != EquipmentSlot.HAND) return; 

        // 2. ตรวจสอบว่าผู้เล่น 'มือเปล่า' หรือไม่
        // ถ้าผู้เล่น "ไม่ได้อุ้ม" อะไรอยู่ และ "ไม่มือเปล่า" ให้ยกเลิกการอุ้ม
        if (!manager.isCarrying(player) && !isHandEmpty(player)) {
            return;
        }

        setCooldown(player);

        // ----- วาง ----- (ถ้ากำลังอุ้มอยู่ เข้าเงื่อนไขนี้)
        if (manager.isCarrying(player)) {
            Location drop = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
            manager.stopCarrying(player, drop);
            event.setCancelled(true);
            return;
        }

        // ----- อุ้ม ----- (ถ้าไม่ได้อุ้มอยู่ เข้าเงื่อนไขนี้ และต้องมือเปล่าตามที่ตรวจสอบไว้แล้ว)
        Material type = event.getClickedBlock().getType();
        if (type.isAir() || type == Material.BEDROCK) return;

        manager.startCarryingBlock(player, event.getClickedBlock());
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handlePlayerQuit(event.getPlayer());
    }
}
