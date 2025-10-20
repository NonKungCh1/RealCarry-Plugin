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
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;

public class CarryListener implements Listener {

    private final RealCarry plugin;
    private final HashMap<UUID, Entity> carriedThings = new HashMap<>();
    private final HashMap<UUID, ItemStack[]> carriedInventories = new HashMap<>();

    private static final List<Material> BLACKLISTED_BLOCKS = Arrays.asList(
            Material.BEDROCK
    );

    public CarryListener(RealCarry plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) return;

        // ตรวจสอบ: ผู้เล่นต้องย่อตัว, มือว่างเปล่า, ไม่อุ้มอยู่, และ "ไม่มี" สิทธิ์ realcarry.carry.entity
        if (player.isSneaking() &&
            player.getInventory().getItemInMainHand().getType() == Material.AIR &&
            !carriedThings.containsKey(player.getUniqueId()) &&
            !player.hasPermission("realcarry.carry.entity")) { // <-- แก้ไขตรงนี้: เติม '!'

            Entity clickedEntity = event.getRightClicked();
            List<String> blacklist = plugin.getLangConfig().getStringList("entity-blacklist");
            
            if (blacklist.contains(clickedEntity.getType().name().toLowerCase())) {
                player.sendMessage(plugin.getMessage("cannot-carry-entity"));
                return;
            }

            player.addPassenger(clickedEntity);
            carriedThings.put(player.getUniqueId(), clickedEntity);
            clickedEntity.setMetadata("CarriedEntity", new FixedMetadataValue(plugin, true));

            player.sendMessage(plugin.getMessage("carry-entity-success"));
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;

        // ตรวจสอบ: ผู้เล่นต้องย่อตัว, มือว่างเปล่า, ไม่อุ้มอยู่, และ "ไม่มี" สิทธิ์ realcarry.carry.block
        if (player.isSneaking() &&
            player.getInventory().getItemInMainHand().getType() == Material.AIR &&
            !carriedThings.containsKey(player.getUniqueId()) &&
            !player.hasPermission("realcarry.carry.block")) { // <-- แก้ไขตรงนี้: เติม '!'

            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null || BLACKLISTED_BLOCKS.contains(clickedBlock.getType())) {
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
            Location spawnLocation = clickedBlock.getLocation().add(0.5, 0, 0.5);
            clickedBlock.setType(Material.AIR);

            FallingBlock fallingBlock = player.getWorld().spawnFallingBlock(spawnLocation, blockData);
            fallingBlock.setGravity(false);
            fallingBlock.setInvulnerable(true);
            fallingBlock.setDropItem(false);
            fallingBlock.setMetadata("CarriedBlock", new FixedMetadataValue(plugin, true));

            player.addPassenger(fallingBlock);
            carriedThings.put(player.getUniqueId(), fallingBlock);

            player.sendMessage(plugin.getMessage("carry-success"));
        } else if (carriedThings.containsKey(player.getUniqueId())) {
            player.sendMessage(plugin.getMessage("already-carrying"));
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking() && carriedThings.containsKey(event.getPlayer().getUniqueId())) {
            dropCarriedThing(event.getPlayer());
            event.getPlayer().sendMessage(plugin.getMessage("place-success"));
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
        if (carriedThings.containsKey(event.getPlayer().getUniqueId())) {
            dropCarriedThing(event.getPlayer());
        }
    }

    private void dropCarriedThing(Player player) {
        UUID playerUUID = player.getUniqueId();
        Entity thing = carriedThings.get(playerUUID);
        if (thing == null) return;

        player.removePassenger(thing);

        if (thing instanceof FallingBlock) {
            FallingBlock fallingBlock = (FallingBlock) thing;
            Block blockToPlace = player.getLocation().getBlock();
            blockToPlace.setBlockData(fallingBlock.getBlockData());

            if (carriedInventories.containsKey(playerUUID)) {
                if (blockToPlace.getState() instanceof InventoryHolder) {
                    ((InventoryHolder) blockToPlace.getState()).getInventory().setContents(carriedInventories.get(playerUUID));
                }
                carriedInventories.remove(playerUUID);
            }
            fallingBlock.remove();
        } else {
            thing.teleport(player.getLocation().add(player.getLocation().getDirection().multiply(1.5)));
            thing.removeMetadata("CarriedEntity", plugin);
        }
        carriedThings.remove(playerUUID);
    }

    public void dropAllCarriedThings() {
        for (UUID playerUUID : carriedThings.keySet()) {
            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                dropCarriedThing(player);
            }
        }
        carriedThings.clear();
        carriedInventories.clear();
    }
}
