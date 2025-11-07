package com.nonkungch.realcarry;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
// import org.bukkit.block.BlockFace; // (ไม่ได้ใช้)
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet; // [เพิ่ม]
import java.util.List;
import java.util.UUID;

public class CarryListener implements Listener {

    private final RealCarry plugin;
    private final HashMap<UUID, Entity> carriedThings = new HashMap<>();
    private final HashMap<UUID, ItemStack[]> carriedInventories = new HashMap<>();
    private BukkitRunnable carryTask;

    // --- [เพิ่ม] ระบบ "สถานะ" แก้บั๊กคลิกเบิ้ล (จำเป็น!) ---
    private final HashSet<UUID> liftingState = new HashSet<>();
    private static final long LIFT_DELAY_TICKS = 3L; // หน่วง 3 Ticks (0.15 วิ) กันบั๊ก
    // ----------------------------------------------------

    private final int slownessLevel;
    private final boolean playerCarryingEnabled;

    private static final List<Material> BLACKLISTED_BLOCKS = Arrays.asList(
            Material.BEDROCK, Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK, Material.STRUCTURE_BLOCK,
            Material.JIGSAW, Material.END_PORTAL_FRAME, Material.END_PORTAL,
            Material.NETHER_PORTAL, Material.BARRIER, Material.LIGHT
    );

    public CarryListener(RealCarry plugin) {
        this.plugin = plugin;
        this.slownessLevel = plugin.getConfig().getInt("carrying-slowness-level", 1);
        this.playerCarryingEnabled = plugin.getConfig().getBoolean("features.player-carrying", true);
    }

    /**
     * [แก้ไข] Task อัปเดตตำแหน่ง, ป้องกันดาเมจ, และท่าทาง
     */
    public void startCarryTask() {
        this.carryTask = new BukkitRunnable() {
            @Override
            public void run() {
                // [เพิ่ม] ใช้ new HashSet กัน ConcurrentModificationException
                for (UUID uuid : new java.util.HashSet<>(carriedThings.keySet())) {
                    Player player = plugin.getServer().getPlayer(uuid);
                    Entity thing = carriedThings.get(uuid);

                    if (player == null || !player.isOnline() || thing == null || thing.isDead()) {
                        if (player != null) {
                            dropCarriedThing(player, player.getLocation(), false);
                        }
                        continue;
                    }

                    // [เพิ่ม] 1. ระบบ Passenger (กันหลุด/กันตาย)
                    if (!player.getPassengers().contains(thing)) {
                        dropCarriedThing(player, player.getLocation(), false);
                        plugin.getLogger().warning("Dropped carried thing for " + player.getName() + " due to desync.");
                        continue;
                    }

                    if (slownessLevel > 0) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, slownessLevel - 1, true, false));
                    }

                    // [แก้ไข] 2. ตำแหน่ง "ระดับมือ"
                    Location targetLoc = player.getLocation().add(0, 1.0, 0); 
                    targetLoc.add(player.getLocation().getDirection().multiply(1.2)); 
                    
                    if (thing instanceof FallingBlock) {
                         targetLoc.setY(targetLoc.getY() - 0.5); 
                    }

                    thing.teleport(targetLoc);
                    thing.setVelocity(new Vector(0, 0, 0)); 
                    thing.setFallDistance(0);
                    
                    // [เพิ่ม] 3 & 4. ป้องกันดาเมจ + บังคับย่อ
                    thing.setInvulnerable(true); 
                    if (thing instanceof Player) {
                        ((Player) thing).setSneaking(true); 
                    }
                }
            }
        };
        this.carryTask.runTaskTimer(plugin, 0L, 2L);
    }

    public void stopCarryTask() {
        if (this.carryTask != null && !this.carryTask.isCancelled()) {
            this.carryTask.cancel();
        }
    }

    /**
     * [แก้ไข] Event อุ้ม Entity (สัตว์, ผู้เล่น)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (carriedThings.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // [เพิ่ม] แก้บั๊กคลิกเบิ้ล
        if (liftingState.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (player.isSneaking() && player.getInventory().getItemInMainHand().getType() == Material.AIR) {
            final Entity clickedEntity = event.getRightClicked(); 
            
            // ตรวจสอบอุ้มผู้เล่น
            if (clickedEntity instanceof Player) {
                if (!playerCarryingEnabled) {
                    player.sendMessage(plugin.getMessage("cannot-carry-player"));
                    event.setCancelled(true);
                    return;
                }
                // [แก้ไข] 5. เพิ่ม Permission
                if (!player.hasPermission("realcarry.carry.player")) {
                    player.sendMessage(plugin.getMessage("no-permission-player"));
                    event.setCancelled(true);
                    return;
                }
                if (((Player) clickedEntity).getGameMode() == GameMode.CREATIVE) {
                    event.setCancelled(true);
                    return;
                }
            } 
            // ตรวจสอบอุ้ม Entity อื่น
            else {
                // [แก้ไข] 5. เพิ่ม Permission
                if (!player.hasPermission("realcarry.carry.entity")) { 
                    player.sendMessage(plugin.getMessage("no-permission-entity"));
                    event.setCancelled(true);
                    return;
                }
                List<String> blacklist = plugin.getLangConfig().getStringList("entity-blacklist");
                if (blacklist.contains(clickedEntity.getType().name().toLowerCase())) {
                    player.sendMessage(plugin.getMessage("cannot-carry-entity"));
                    return;
                }
            }

            // --- [แก้ไข] หน่วงการอุ้ม 3 Ticks (แก้บั๊ก) ---
            event.setCancelled(true); 
            liftingState.add(player.getUniqueId()); // 1. ตั้งสถานะ "กำลังจะอุ้ม"

            new BukkitRunnable() {
                @Override
                public void run() {
                    // 2. อุ้มจริง (ใน Tick ถัดไป)
                    if (player.isOnline() && clickedEntity.isValid()) {
                        player.addPassenger(clickedEntity); // 1. ระบบ Passenger
                        carriedThings.put(player.getUniqueId(), clickedEntity);
                        clickedEntity.setMetadata("CarriedEntity", new FixedMetadataValue(plugin, true));
                        clickedEntity.setPersistent(true); 
                        clickedEntity.setInvulnerable(true); // 3. กันตาย
                        player.sendMessage(plugin.getMessage("carry-entity-success"));
                    }
                    liftingState.remove(player.getUniqueId()); // 3. ลบสถานะ
                }
            }.runTaskLater(plugin, LIFT_DELAY_TICKS); // หน่วง 3 Ticks
            // ---------------------------------
        }
    }

    /**
     * [แก้ไข] Event อุ้ม "บล็อก" และ วาง "ทุกอย่าง"
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) return;

        // --- ส่วนที่ 1: ตรวจสอบการ "วาง" (โค้ดเดิมของคุณถูกต้อง) ---
        if (carriedThings.containsKey(player.getUniqueId())) {
            event.setCancelled(true); 

            if (player.isSneaking() && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Block clickedBlock = event.getClickedBlock();
                if (clickedBlock == null) return; 

                Location placeLocation = clickedBlock.getRelative(event.getBlockFace()).getLocation();

                if (player.getLocation().distance(placeLocation) > 4.5) { 
                    player.sendMessage(plugin.getMessage("place-too-far"));
                    return;
                }
                if (!placeLocation.getBlock().getType().isAir()) {
                    player.sendMessage(plugin.getMessage("place-obstructed"));
                    return;
                }
                dropCarriedThing(player, placeLocation, true); 
                player.sendMessage(plugin.getMessage("place-success"));
            }
            return; 
        }
        
        // [เพิ่ม] แก้บั๊กคลิกเบิ้ล
        if (liftingState.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // --- ส่วนที่ 2: ตรวจสอบการ "อุ้ม" บล็อก ---
        if (player.isSneaking() &&
                event.getAction() == Action.RIGHT_CLICK_BLOCK &&
                player.getInventory().getItemInMainHand().getType() == Material.AIR) {

            // [แก้ไข] 5. เพิ่ม Permission
            if (!player.hasPermission("realcarry.carry.block")) {
                player.sendMessage(plugin.getMessage("no-permission-block"));
                event.setCancelled(true);
                return;
            }

            final Block clickedBlock = event.getClickedBlock(); 
            if (clickedBlock == null || BLACKLISTED_BLOCKS.contains(clickedBlock.getType()) || clickedBlock.getType().isAir()) {
                player.sendMessage(plugin.getMessage("cannot-carry"));
                return;
            }
            
            // --- [แก้ไข] หน่วงการอุ้ม 3 Ticks (แก้บั๊ก) ---
            event.setCancelled(true); // (เพิ่ม setCancelled กันบั๊กเปิดหีบ)
            liftingState.add(player.getUniqueId()); // 1. ตั้งสถานะ "กำลังจะอุ้ม"

            final Location spawnLocation = player.getLocation().add(0, 1.0, 0).add(player.getLocation().getDirection().multiply(1.2));
            spawnLocation.setY(spawnLocation.getY() - 0.5); 
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    // (เช็คอีกรอบว่าบล็อกยังอยู่ไหม)
                    if (!player.isOnline() || clickedBlock.getType().isAir()) {
                        liftingState.remove(player.getUniqueId());
                        return; 
                    }
                    
                    // บันทึก Inventory (ถ้ามี)
                    if (clickedBlock.getState() instanceof InventoryHolder) {
                        InventoryHolder container = (InventoryHolder) clickedBlock.getState();
                        ItemStack[] items = container.getInventory().getContents();
                        carriedInventories.put(player.getUniqueId(), Arrays.stream(items)
                                .map(item -> item == null ? null : item.clone())
                                .toArray(ItemStack[]::new));
                        container.getInventory().clear();
                    }
                    
                    BlockData blockData = clickedBlock.getBlockData();
                    clickedBlock.setType(Material.AIR); // ลบบล็อกจริง

                    // 2. อุ้มจริง (ใน Tick ถัดไป)
                    FallingBlock fallingBlock = player.getWorld().spawnFallingBlock(spawnLocation, blockData);
                    fallingBlock.setGravity(false);
                    fallingBlock.setInvulnerable(true); // 3. กันตาย
                    fallingBlock.setDropItem(false); 
                    fallingBlock.setHurtEntities(false);
                    fallingBlock.setMetadata("CarriedBlock", new FixedMetadataValue(plugin, true));

                    player.addPassenger(fallingBlock); // 1. ระบบ Passenger
                    carriedThings.put(player.getUniqueId(), fallingBlock);
                    player.sendMessage(plugin.getMessage("carry-success"));
                    
                    liftingState.remove(player.getUniqueId()); // 3. ลบสถานะ
                }
            }.runTaskLater(plugin, LIFT_DELAY_TICKS); // หน่วง 3 Ticks
            // ---------------------------------
        }
    }

    @EventHandler
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock && event.getEntity().hasMetadata("CarriedBlock")) {
            event.setCancelled(true); 
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        liftingState.remove(event.getPlayer().getUniqueId()); // [เพิ่ม] ลบสถานะ
        if (carriedThings.containsKey(event.getPlayer().getUniqueId())) {
            dropCarriedThing(event.getPlayer(), event.getPlayer().getLocation(), false);
        }
    }

    /**
     * [แก้ไข] เมธอด "วาง" หรือ "ทำตก"
     */
    public void dropCarriedThing(Player player, Location dropLocation, boolean isPlacing) {
        UUID playerUUID = player.getUniqueId();
        Entity thing = carriedThings.get(playerUUID);
        if (thing == null) return;

        // --- [เพิ่ม] แก้ไขการวาง ---
        player.removePassenger(thing); // 1. เอาออกจากตัว

        if (slownessLevel > 0) {
            player.removePotionEffect(PotionEffectType.SLOWNESS); 
        }
        
        // 3 & 4. คืนค่าอมตะ และ ท่าทาง
        thing.setInvulnerable(false);
        if (thing instanceof Player) {
            ((Player) thing).setSneaking(false); 
        }
        // -------------------------

        if (thing instanceof FallingBlock) {
            FallingBlock fallingBlock = (FallingBlock) thing;
            Block blockToPlace = dropLocation.getBlock();
            
            blockToPlace.setBlockData(fallingBlock.getBlockData()); 

            if (carriedInventories.containsKey(playerUUID)) {
                if (blockToPlace.getState() instanceof InventoryHolder) {
                    ((InventoryHolder) blockToPlace.getState()).getInventory().setContents(carriedInventories.get(playerUUID));
                }
                carriedInventories.remove(playerUUID);
            }
            fallingBlock.remove(); 
        } 
        else {
            if (isPlacing) {
                thing.teleport(dropLocation.add(0, 0.5, 0));
            } else {
                thing.teleport(dropLocation);
            }
            thing.removeMetadata("CarriedEntity", plugin);
        }
        
        carriedThings.remove(playerUUID); 
        liftingState.remove(playerUUID); // [เพิ่ม] ลบสถานะ
    }

    public void dropAllCarriedThings() {
        for (UUID playerUUID : new java.util.HashSet<>(carriedThings.keySet())) {
            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                dropCarriedThing(player, player.getLocation(), false);
            }
        }
        carriedThings.clear();
        carriedInventories.clear();
        liftingState.clear(); // [เพิ่ม] ล้างสถานะทั้งหมด
    }

    // --- API Methods (สำหรับให้ RealCarry.java เรียกใช้) ---
    public boolean isPlayerCarrying(Player player) {
        return carriedThings.containsKey(player.getUniqueId());
    }

    public Entity getCarriedEntity(Player player) {
        return carriedThings.get(player.getUniqueId());
    }
}
