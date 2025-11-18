package com.nonkungch.realcarry;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class CarryListener implements Listener {

    private final RealCarry plugin;
    private final CarryingManager carryingManager;

    public CarryListener(RealCarry plugin, CarryingManager carryingManager) {
        this.plugin = plugin;
        this.carryingManager = carryingManager;
    }

    /**
     * ทำงานเมื่อผู้เล่น Shift + คลิกขวา ที่ Entity (เพื่ออุ้ม)
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();

        // 1. ตรวจสอบเงื่อนไขพื้นฐาน
        if (!player.isSneaking() || !player.hasPermission("realcarry.use")) {
            return;
        }
        
        // 2. ถ้ากำลังอุ้มของอยู่แล้ว ต้องวางก่อน
        if (carryingManager.isCarrying(player)) {
            // ยกเลิก event เพื่อป้องกันการโต้ตอบกับ Entity ในขณะที่กำลังอุ้ม
            event.setCancelled(true); 
            return;
        }

        // 3. ตรวจสอบว่าเป็นสัตว์ (หรือ Entity ที่อุ้มได้)
        if (clickedEntity instanceof Animals) {
            carryingManager.startCarryingEntity(player, clickedEntity);
            event.setCancelled(true); // ยกเลิก event เพื่อไม่ให้เกิดการทำงานปกติ
        }
    }

    /**
     * ทำงานเมื่อผู้เล่น Shift + คลิกขวา (เพื่ออุ้มบล็อก หรือ วาง)
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // 1. ตรวจสอบเงื่อนไขพื้นฐาน
        if (!player.isSneaking() || !player.hasPermission("realcarry.use")) {
            return;
        }

        // 2. ต้องเป็นการคลิกขวาที่บล็อกเท่านั้น
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        
        // 3. ถ้ากำลังอุ้มอะไรอยู่ = "วาง"
        if (carryingManager.isCarrying(player)) {
            
            // หาตำแหน่งที่จะวาง: บนบล็อกที่คลิก (ใช้ getBlockFace เพื่อหาบล็อกถัดไป)
            Location dropLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
            
            carryingManager.stopCarrying(player, dropLoc);
            event.setCancelled(true); // ยกเลิก event เพื่อป้องกันการวางไอเท็มในมือ

        } else {
            // 4. ถ้าไม่ได้อุ้มอะไร = "อุ้มบล็อก"
            Material type = event.getClickedBlock().getType();
            
            // ป้องกันการอุ้ม Bedrock หรือบล็อกที่ทำลายไม่ได้
            if (type.isAir() || type == Material.BEDROCK || type.getHardness() < 0) {
                return;
            }
            
            carryingManager.startCarryingBlock(player, event.getClickedBlock());
            event.setCancelled(true); // ยกเลิก event เพื่อป้องกันการวางไอเท็มในมือ
        }
    }

    /**
     * เคลียร์ของที่อุ้มเมื่อผู้เล่นออกจากเซิร์ฟเวอร์
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        carryingManager.handlePlayerQuit(event.getPlayer());
    }
}
