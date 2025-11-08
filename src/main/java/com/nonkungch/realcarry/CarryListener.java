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

        // ต้องกด Shift และมีสิทธิ์
        if (!player.isSneaking() || !player.hasPermission("realcarry.use")) {
            return;
        }

        // ถ้าอุ้มของอยู่แล้ว
        if (carryingManager.isCarrying(player)) {
            player.sendMessage(plugin.getMsg("already-carrying"));
            event.setCancelled(true);
            return;
        }

        // ตรวจสอบว่าเป็นสัตว์ (หรือ Entity ที่อุ้มได้)
        // (เราจำกัดแค่ Animals เพื่อความปลอดภัย)
        if (clickedEntity instanceof Animals) {
            carryingManager.startCarryingEntity(player, clickedEntity);
            event.setCancelled(true);
        }
    }

    /**
     * ทำงานเมื่อผู้เล่น Shift + คลิกขวา (เพื่ออุ้มบล็อก หรือ วาง)
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // ต้องกด Shift และมีสิทธิ์
        if (!player.isSneaking() || !player.hasPermission("realcarry.use")) {
            return;
        }

        // ต้องเป็นการคลิกขวาที่บล็อกเท่านั้น
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        // ถ้ากำลังอุ้มอะไรอยู่ = "วาง"
        if (carryingManager.isCarrying(player)) {
            // หาตำแหน่งที่จะวาง (บนบล็อกที่คลิก)
            Location dropLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
            // ขยับจุดวางให้ตรงกลางบล็อก (สำหรับสัตว์)
            dropLoc.add(0.5, 0, 0.5); 
            
            carryingManager.stopCarrying(player, dropLoc);
            event.setCancelled(true);
            
        } else {
            // ถ้าไม่ได้อุ้มอะไร = "อุ้มบล็อก"
            Material type = event.getClickedBlock().getType();
            
            // ป้องกันการอุ้ม Bedrock หรือบล็อกที่ทำลายไม่ได้
            if (type.isAir() || type == Material.BEDROCK || type.getHardness() < 0) {
                return;
            }
            
            carryingManager.startCarryingBlock(player, event.getClickedBlock());
            event.setCancelled(true);
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
