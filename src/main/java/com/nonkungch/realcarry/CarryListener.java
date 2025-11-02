package com.nonkungch.realcarry;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
        this.slownessLevel = plugin.getConfig().getInt("carrying-slowness-level", 1);
        this.playerCarryingEnabled = plugin.getConfig().getBoolean("features.player-carrying", true);
    }

    public void startCarryTask() {
        this.carryTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new java.util.HashSet<>(carriedThings.keySet())) {
                    Player player = plugin.getServer().getPlayer(uuid);
                    Entity thing = carriedThings.get(uuid);

                    if (player == null || !player.isOnline() || thing == null || thing.isDead()) {
                        if (player != null) {
                            dropCarriedThing(player, player.getLocation(), false);
                        }
                        continue;
                    }

                    if (!player.getPassengers().contains(thing)) {
                        dropCarriedThing(player, player.getLocation(), false);
                        plugin.getLogger().warning("Dropped carried thing for " + player.getName() + " due to desync.");
                        continue;
                    }

                    // 1. ใส่ Slowness
                    if (slownessLevel > 0) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, slownessLevel - 1, true, false));
                    }

                    // 2. เทเลพอร์ตของไป "ระดับมือ"
                    Location targetLoc = player.getLocation().add(0, 1.0, 0); 
                    targetLoc.add(player.getLocation().getDirection().multiply(1.2)); 
                    
                    if (thing instanceof FallingBlock) {
                         targetLoc.setY(targetLoc.getY() - 0.5);
                    }

                    thing.teleport(targetLoc);
                    thing.setVelocity(new Vector(0, 0, 0)); 
                    thing.setFallDistance(0);
                    
                    // --- [เพิ่ม] ทำให้เป็นอมตะ และบังคับย่อ (นั่ง) ---
                    thing.setInvulnerable(true); // ป้องกันดาเมจทุกชนิด
                    if (thing instanceof Player) {
                        ((Player) thing).setSneaking(true); // บังคับท่าย่อ (เหมือนนั่ง)
                    }
                    // ---------------------------------------------
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (carriedThings.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (player.isSneaking() && player.getInventory().getItemInMainHand().getType() == Material.AIR) {
            Entity clickedEntity = event.getRightClicked();
            
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
                if (((Player) clickedEntity).getGameMode() == GameMode.CREATIVE) {
                    event.setCancelled(true);
                    return;
                }
            } 
            else {
                if (false) { 
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

            player.addPassenger(clickedEntity);
            carriedThings.put(player.getUniqueId(), clickedEntity);
            clickedEntity.setMetadata("CarriedEntity", new FixedMetadataValue(plugin, true));
            clickedEntity.setPersistent(true); 
            
            // --- [เพิ่ม] เปิดอมตะทันทีที่อุ้ม ---
            clickedEntity.setInvulnerable(true);

            player.sendMessage(plugin.getMessage("carry-entity-success"));
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) return;

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

        if (player.isSneaking() &&
                event.getAction() == Action.RIGHT_CLICK_BLOCK &&
                player.getInventory().getItemInMainHand().getType() == Material.AIR) {

            if (false) { 
                player.sendMessage(plugin.getMessage("no-permission-block"));
                event.setCancelled(true);
                return;
            }

            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null || BLACKLISTED_BLOCKS.contains(clickedBlock.getType()) || clickedBlock.getType().isAir()) {
                player.sendMessage(plugin.getMessage("cannot-carry"));
                return;
            }

            if (clickedBlock.getState() instanceof InventoryHolder) {
                InventoryHolder container = (InventoryHolder) clickedBlock.getState();
                ItemStack[] items = container.getInventory().getContents();
                carriedInventories.put(player.getUniqueId(), Arrays.stream(items)
                        .map(item -> item == null ? null : item.clone())
                        .toArray(ItemStack[]::new));
                container.getInventory().clear();
            }
            
            if (player.getGameMode() == GameMode.SURVIVAL) {
                 event.setCancelled(true);
            }

            BlockData blockData = clickedBlock.getBlockData();
            Location spawnLocation = player.getLocation().add(0, 1.0, 0).add(player.getLocation().getDirection().multiply(1.2));
            spawnLocation.setY(spawnLocation.getY() - 0.5); 
            
            clickedBlock.setType(Material.AIR); 

            FallingBlock fallingBlock = player.getWorld().spawnFallingBlock(spawnLocation, blockData);
            fallingBlock.setGravity(false);
            fallingBlock.setInvulnerable(true); // --- [แก้ไข] เปิดอมตะทันทีที่อุ้ม
            fallingBlock.setDropItem(false); 
            fallingBlock.setHurtEntities(false);
            fallingBlock.setMetadata("CarriedBlock", new FixedMetadataValue(plugin, true));

            player.addPassenger(fallingBlock);
            carriedThings.put(player.getUniqueId(), fallingBlock);

            player.sendMessage(plugin.getMessage("carry-success"));
        }
    }

    // --- [ลบ] เราไม่ต้องการ Event นี้แล้ว เพราะใช้ setInvulnerable() แทน ---
    /*
    @EventHandler
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock && event.getEntity().hasMetadata("CarriedBlock")) {
            event.setCancelled(true); 
        }
    }
    */
    // (หมายเหตุ: FallingBlock ที่ setInvulnerable(true) จะไม่กลายเป็นบล็อกเมื่อแตะพื้นอยู่แล้ว)
    // --- [แก้ไข] เพิ่ม Event นี้กลับมา (ปลอดภัยไว้ก่อน) ---
    @EventHandler
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock && event.getEntity().hasMetadata("CarriedBlock")) {
            event.setCancelled(true); 
        }
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (carriedThings.containsKey(event.getPlayer().getUniqueId())) {
            dropCarriedThing(event.getPlayer(), event.getPlayer().getLocation(), false);
        }
    }

    public void dropCarriedThing(Player player, Location dropLocation, boolean isPlacing) {
        UUID playerUUID = player.getUniqueId();
        Entity thing = carriedThings.get(playerUUID);
        if (thing == null) return;

        player.removePassenger(thing);

        if (slownessLevel > 0) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }

        // --- [เพิ่ม] ปิดอมตะ และคืนท่าทางปกติ ---
        thing.setInvulnerable(false);
        if (thing instanceof Player) {
            ((Player) thing).setSneaking(false); // คืนท่าทางปกติ
        }
        // -----------------------------------

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
        } else {
            if (isPlacing) {
                thing.teleport(dropLocation.add(0, 0.5, 0));
            } else {
                thing.teleport(dropLocation);
            }
            thing.removeMetadata("CarriedEntity", plugin);
        }
        
        carriedThings.remove(playerUUID);
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
    }

    public HashMap<UUID, Entity> getCarriedThings() {
        return this.carriedThings;
    }
}
