package com.humankindgames.claysoldiers;

import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class ClaySoldierListener
        implements Listener {
    private static final String SPAWN_FAILURE_MESSAGE = "Clay soldier spawn failed. Check the server console for the stack trace.";

    private final ClaySoldierItems items;
    private final ClaySoldierService soldiers;
    private final ClaySoldierSettings settings;

    public ClaySoldierListener(ClaySoldierItems items, ClaySoldierService soldiers, ClaySoldierSettings settings) {
        this.items = items;
        this.soldiers = soldiers;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDollUse(PlayerInteractEvent event) {
        if( event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) ) {
            return;
        }

        ItemStack item = event.getItem();
        Optional<ClayTeam> team = this.items.getDollTeam(item);
        if( team.isEmpty() ) {
            return;
        }

        Optional<Location> spawnLocation = resolveSpawnLocation(event);
        if( spawnLocation.isEmpty() ) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        int requestedCount = player.isSneaking() ? 1 : item.getAmount();
        int spawnCount = Math.max(1, Math.min(this.settings.maxSpawnPerUse(), requestedCount));
        ClaySoldierRole role = this.items.getDollRole(item);

        try {
            this.soldiers.spawnSoldiers(team.get(), role, spawnLocation.get(), spawnCount);
        } catch( RuntimeException ex ) {
            player.sendMessage(SPAWN_FAILURE_MESSAGE);
            this.soldiers.plugin().getLogger().log(Level.SEVERE, "Failed to spawn clay soldier from doll", ex);
            return;
        }

        if( player.getGameMode() != GameMode.CREATIVE ) {
            consumeDolls(player, item, spawnCount);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSoldierDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if( !(entity instanceof ArmorStand soldier) || !this.soldiers.isSoldier(soldier) ) {
            return;
        }

        event.setCancelled(true);

        double damage = Math.max(1.0D, event.getFinalDamage());
        Entity attacker = null;
        if( event instanceof EntityDamageByEntityEvent damageByEntity ) {
            attacker = damageByEntity.getDamager();
            if( attacker instanceof Player player ) {
                damage = player.getGameMode() == GameMode.CREATIVE && this.settings.creativePlayerInstantKill()
                        ? this.settings.maxHealth() * 10.0D
                        : Math.max(damage, this.settings.playerDamage());
            }
        }

        this.soldiers.damageSoldier(soldier, damage, attacker);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if( this.soldiers.isSoldier(event.getRightClicked()) ) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSoldierInteract(PlayerInteractAtEntityEvent event) {
        if( this.soldiers.isSoldier(event.getRightClicked()) ) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreativeInventory(InventoryCreativeEvent event) {
        if( this.items.isSoldierDoll(event.getCursor()) ) {
            event.getCursor().setAmount(Math.min(event.getCursor().getAmount(), this.settings.dollStackSize()));
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for( Entity entity : event.getChunk().getEntities() ) {
            this.soldiers.registerIfSoldier(entity);
        }
    }

    private Optional<Location> resolveSpawnLocation(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        if( clickedBlock != null ) {
            Block spawnBlock = clickedBlock.getRelative(event.getBlockFace());
            return Optional.of(spawnBlock.getLocation().add(0.5D, 0.0D, 0.5D));
        }

        Block targetBlock = event.getPlayer().getTargetBlockExact(6);
        if( targetBlock != null && targetBlock.getType() != Material.AIR ) {
            return Optional.of(targetBlock.getRelative(event.getPlayer().getFacing()).getLocation().add(0.5D, 0.0D, 0.5D));
        }

        Location playerLocation = event.getPlayer().getLocation();
        Vector direction = horizontalDirection(event.getPlayer());
        Location location = playerLocation.clone().add(direction.multiply(1.5D));
        location.setY(playerLocation.getY());
        return Optional.of(location);
    }

    private Vector horizontalDirection(Player player) {
        Vector direction = player.getLocation().getDirection();
        direction.setY(0.0D);
        if( direction.lengthSquared() < 0.0001D ) {
            direction = player.getFacing().getDirection();
            direction.setY(0.0D);
        }

        return direction.normalize();
    }

    private void consumeDolls(Player player, ItemStack item, int amount) {
        int remaining = item.getAmount() - amount;
        if( remaining <= 0 ) {
            player.getInventory().setItemInMainHand(null);
            return;
        }

        item.setAmount(remaining);
        player.getInventory().setItemInMainHand(item);
    }
}
