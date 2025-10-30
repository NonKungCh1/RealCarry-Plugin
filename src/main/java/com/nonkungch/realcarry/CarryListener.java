package com.nonkungch.realcarry;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import java.util.List;
import java.util.UUID;

public class CarryListener implements Listener {

    private final RealCarry plugin;
    private final HashMap<UUID, Entity> carriedThings = new HashMap<>();
    private final HashMap<UUID, ItemStack[]> carriedInventories = new HashMap<>();
    private BukkitRunnable carryTask;

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
        // โหลดค่าจาก Config
        this.slownessLevel = plugin.getConfig().getInt("carrying-slowness-level", 1);
        this.playerCarryingEnabled = plugin.getConfig().getBoolean("features.player-carrying", true);
    }

    /**
     * เริ่ม Task ที่จะอัปเดตตำแหน่งของที่อุ้ม + ใส่ Slowness
     */
    public void startCarryTask() {
        this.carryTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : carriedThings.keySet()) {
                    Player player = plugin.getServer().getPlayer(uuid);
                    Entity thing = carriedThings.get(uuid);

                    if (player == null || !player.isOnline() || thing == null || thing.isDead()) {
                        // ถ้าผู้เล่นหลุด หรือ ของหาย (เช่น /kill) ให้ดรอป
                        if (player != null) {
                            dropCarriedThing(player, player.getLocation(), false);
                        }
                        continue;
                    }

                    // 1. ใส่ Slowness
                    if (slownessLevel > 0) {
                        // (level - 1) เพราะ PotionEffect เริ่มนับที่ 0 (0 = level I)
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 30, slownessLevel - 1, true, false));
                    }

                    // 2. เทเลพอร์ตของไปข้างหน้าผู้เล่น
                    Location targetLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.5));
                    
                    // ปรับความสูงเล็กน้อยสำหรับ FallingBlock
                    if (thing instanceof FallingBlock) {
                         targetLoc.setY(targetLoc.getY() - 0.5);
                    }

                    thing.teleport(targetLoc);
                    thing.setVelocity(new Vector(0, 0, 0)); // ป้องกันการสั่น
                    thing.setFallDistance(0); // ป้องกันการพัง
                }
            }
        };
        // รันทุก 2 Ticks (0.1 วินาที) เพื่อความสมดุลระหว่างความลื่นไหลและ Performance
        this.carryTask.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * หยุด Task (ตอนปิดปลั๊กอิน)
     */
    public void stopCarryTask() {
        if (this.carryTask != null && !this.carryTask.isCancelled()) {
            this.carryTask.cancel();
        }
    }

    /**
     * Event สำหรับการ "อุ้ม" Entity หรือ Player
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) return;

        // ถ้ากำลังอุ้มอยู่: บล็อคการคลิกขวาที่ Entity อื่น
        if (carriedThings.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // ถ้าไม่อุ้ม -> พยายาม "อุ้ม"
        if (player.isSneaking() && player.getInventory().getItemInMainHand().getType() == Material.AIR) {
            Entity clickedEntity = event.getRightClicked();
            
            // 1. ตรวจสอบการอุ้มผู้เล่น
            if (clickedEntity instanceof Player) {
                if (!playerCarryingEnabled) {
                    player.sendMessage(plugin.getMessage("cannot-carry-player"));
                    event.setCancelled(true);
                    return;
                }
                if (!player.hasPermission("realcarry.carry.player")) {
                    player.sendMessage(plugin.getMessage("no-permission-player"));
                    event.setCancelled(true);
                    return;
                }
                // (อาจจะเพิ่มเงื่อนไข: ห้ามอุ้มคนใน Gamemode Creative)
                if (((Player) clickedEntity).getGameMode() == GameMode.CREATIVE) {
                    event.setCancelled(true);
                    return;
                }
            } 
            // 2. ตรวจสอบการอุ้ม Entity อื่น
            else {
                // (โค้ดนี้คือทุกคนอุ้มได้ ตามที่คุณขอครั้งล่าสุด)
                // ถ้าอยากให้ต้องใช้สิทธิ์ ให้แก้บรรทัดล่างเป็น:
                // if (!player.hasPermission("realcarry.carry.entity")) {
                if (false) { // <-- แก้ตรงนี้ถ้าอยากเช็ค Perm
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

            // --- ทำการอุ้ม ---
            carriedThings.put(player.getUniqueId(), clickedEntity);
            clickedEntity.setMetadata("CarriedEntity", new FixedMetadataValue(plugin, true));
            
            // ทำให้ Entity อยู่รอด (ไม่ Despawn)
            clickedEntity.setPersistent(true); 

            player.sendMessage(plugin.getMessage("carry-entity-success"));
            event.setCancelled(true);
        }
    }

    /**
     * Event สำหรับการ "อุ้ม" บล็อก และการ "วาง" ทุกอย่าง
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) return;

        // --- ส่วนที่ 1: ตรวจสอบการ "วาง" (ถ้ากำลังอุ้มอยู่) ---
        if (carriedThings.containsKey(player.getUniqueId())) {
            event.setCancelled(true); // บล็อคทุกการกระทำถ้าไม่ตรงเงื่อนไขการวาง

            // เงื่อนไขการวาง: ย่อ + คลิกขวาลงบล็อก
            if (player.isSneaking() && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Block clickedBlock = event.getClickedBlock();
                if (clickedBlock == null) return;

                // หาตำแหน่งที่จะวาง (บนบล็อกที่คลิก)
                Location placeLocation = clickedBlock.getRelative(event.getBlockFace()).getLocation();

                // ตรวจสอบระยะ 4 บล็อก
                if (player.getLocation().distance(placeLocation) > 4.5) { // 4.5 เพื่อความยืดหยุ่น
                    player.sendMessage(plugin.getMessage("place-too-far"));
                    return;
                }

                // ตรวจสอบว่าที่ที่จะวางว่างเปล่าหรือไม่
                if (!placeLocation.getBlock().getType().isAir()) {
                    player.sendMessage(plugin.getMessage("place-obstructed"));
                    return;
                }

                // ทำการวาง
                dropCarriedThing(player, placeLocation, true); // true = เป็นการวางปกติ
                player.sendMessage(plugin.getMessage("place-success"));
            }
            return; // จบการทำงาน ไม่ต้องเช็คการอุ้ม
        }

        // --- ส่วนที่ 2: ตรวจสอบการ "อุ้ม" บล็อก (ถ้ายังไม่อุ้ม) ---
        // เงื่อนไขการอุ้ม: ย่อ + คลิกขวาบล็อก + มือเปล่า
        if (player.isSneaking() &&
                event.getAction() == Action.RIGHT_CLICK_BLOCK &&
                player.getInventory().getItemInMainHand().getType() == Material.AIR) {

            // (โค้ดนี้คือทุกคนอุ้มได้ ตามที่คุณขอครั้งล่าสุด)
            // ถ้าอยากให้ต้องใช้สิทธิ์ ให้แก้บรรทัดล่างเป็น:
            // if (!player.hasPermission("realcarry.carry.block")) {
            if (false) { // <-- แก้ตรงนี้ถ้าอยากเช็ค Perm
                player.sendMessage(plugin.getMessage("no-permission-block"));
                event.setCancelled(true);
                return;
            }

            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null || BLACKLISTED_BLOCKS.contains(clickedBlock.getType()) || clickedBlock.getType().isAir()) {
                player.sendMessage(plugin.getMessage("cannot-carry"));
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
            
            if (player.getGameMode() == GameMode.SURVIVAL) {
                // สำหรับ Survival, เราต้องยกเลิก event เพื่อไม่ให้เปิด GUI หีบ
                 event.setCancelled(true);
            }

            BlockData blockData = clickedBlock.getBlockData();
            Location spawnLocation = clickedBlock.getLocation().add(0.5, 0.5, 0.5); // ปรับ Y ให้อยู่กลางบล็อก
            clickedBlock.setType(Material.AIR); // ทำให้บล็อกเดิมหายไป

            // สร้าง FallingBlock
            FallingBlock fallingBlock = player.getWorld().spawnFallingBlock(spawnLocation, blockData);
            fallingBlock.setGravity(false);
            fallingBlock.setInvulnerable(true);
            fallingBlock.setDropItem(false); // ไอเทมไม่ดรอป
            fallingBlock.setHurtEntities(false);
            fallingBlock.setMetadata("CarriedBlock", new FixedMetadataValue(plugin, true));

            // ทำการอุ้ม
            carriedThings.put(player.getUniqueId(), fallingBlock);
            player.sendMessage(plugin.getMessage("carry-success"));
        }
    }

    /**
     * กันไม่ให้ FallingBlock ที่เราอุ้มอยู่กลายเป็นบล็อกเมื่อแตะพื้น
     */
    @EventHandler
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock && event.getEntity().hasMetadata("CarriedBlock")) {
            event.setCancelled(true); // ห้ามตกลงพื้น
        }
    }

    /**
     * จัดการเมื่อผู้เล่นออกจากเซิร์ฟเวอร์
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (carriedThings.containsKey(event.getPlayer().getUniqueId())) {
            // ดรอปของ ณ จุดที่ผู้เล่นยืนอยู่
            dropCarriedThing(event.getPlayer(), event.getPlayer().getLocation(), false);
        }
    }

    /**
     * เมธอดหลักในการ "วาง" หรือ "ทำตก" (Public สำหรับ API)
     * @param player ผู้เล่น
     * @param dropLocation ตำแหน่งที่จะวาง
     * @param isPlacing true ถ้าเป็นการวางปกติ (คลิกขวา), false ถ้าเป็นการทำตก (เช่น ออกเกม)
     */
    public void dropCarriedThing(Player player, Location dropLocation, boolean isPlacing) {
        UUID playerUUID = player.getUniqueId();
        Entity thing = carriedThings.get(playerUUID);
        if (thing == null) return;

        // ลบ Slowness
        if (slownessLevel > 0) {
            player.removePotionEffect(PotionEffectType.SLOW);
        }

        if (thing instanceof FallingBlock) {
            FallingBlock fallingBlock = (FallingBlock) thing;
            Block blockToPlace = dropLocation.getBlock();
            
            // วางบล็อกลงไป
            blockToPlace.setBlockData(fallingBlock.getBlockData());

            // คืนค่า Inventory (ถ้ามี)
            if (carriedInventories.containsKey(playerUUID)) {
                if (blockToPlace.getState() instanceof InventoryHolder) {
                    ((InventoryHolder) blockToPlace.getState()).getInventory().setContents(carriedInventories.get(playerUUID));
                }
                carriedInventories.remove(playerUUID);
            }
            fallingBlock.remove(); // ลบ FallingBlock ทิ้ง
        } else {
            // ถ้าเป็น Entity หรือ Player
            if (isPlacing) {
                // วางลงที่จุดคลิก + 0.5 Y
                thing.teleport(dropLocation.add(0, 0.5, 0));
            } else {
                // ทำตก (วางที่เท้าผู้เล่น)
                thing.teleport(dropLocation);
            }
            thing.removeMetadata("CarriedEntity", plugin);
            // คืนค่า Persistent (ถ้าคุณต้องการให้มันกลับไป Despawn ได้)
            // thing.setPersistent(false); 
        }
        
        // ลบออกจากรายการอุ้ม
        carriedThings.remove(playerUUID);
    }

    /**
     * ดรอปของทั้งหมด (ตอนปิดเซิร์ฟ)
     */
    public void dropAllCarriedThings() {
        // ทำสำเนา KeySet เพื่อป้องกัน ConcurrentModificationException
        for (UUID playerUUID : new java.util.HashSet<>(carriedThings.keySet())) {
            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                dropCarriedThing(player, player.getLocation(), false);
            }
        }
        carriedThings.clear();
        carriedInventories.clear();
    }

    // --- API Methods (สำหรับให้ RealCarry.java เรียกใช้) ---
    public HashMap<UUID, Entity> getCarriedThings() {
        return this.carriedThings;
    }
}
