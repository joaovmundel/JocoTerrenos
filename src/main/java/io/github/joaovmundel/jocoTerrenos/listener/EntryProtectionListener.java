package io.github.joaovmundel.jocoTerrenos.listener;

import io.github.joaovmundel.jocoTerrenos.protection.Claim;
import io.github.joaovmundel.jocoTerrenos.service.MessageService;
import io.github.joaovmundel.jocoTerrenos.service.ProtectionService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.Plugin;

/**
 * Keeps players out of lands that are not public: walking, riding and teleporting in is refused, and anyone who
 * ends up inside anyway (a home set while the land was public, a login inside it, a land that just turned private)
 * is taken to the nearest spot outside its border instead of being left stuck in a place they cannot walk in.
 */
public final class EntryProtectionListener implements Listener {

    private final Plugin plugin;
    private final ProtectionService protection;

    public EntryProtectionListener(Plugin plugin, ProtectionService protection) {
        this.plugin = plugin;
        this.protection = protection;
    }

    /** Runs on every step, so the cheap checks come first: nothing to do unless the player changed block column. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        Claim target = protection.claimAt(to);
        if (protection.canEnter(player, target)) {
            return;
        }

        Claim origin = protection.claimAt(from);
        if (origin != null && origin.getId() == target.getId()) {
            // already inside: leading them out is the only way they are not stuck
            event.setTo(protection.exitFor(player, target, to));
            protection.notifyExpelled(player, target);
            return;
        }
        event.setCancelled(true);
        protection.notifyDenied(player, ProtectionService.MESSAGE_PRIVATE, MessageService.placeholders("owner", target.getOwnerKey()));
    }

    /** /home, /back, ender pearls, plugin teleports: a destination inside a private land becomes its border. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        Player player = event.getPlayer();
        Claim target = protection.claimAt(to);
        if (protection.canEnter(player, target)) {
            return;
        }
        event.setTo(protection.exitFor(player, target, to));
        protection.notifyExpelled(player, target);
    }

    /** Someone who logs in inside a land that is private now (or whose owner changed) is moved out. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                Location where = player.getLocation();
                Claim here = protection.claimAt(where);
                if (!protection.canEnter(player, here)) {
                    player.teleport(protection.exitFor(player, here, where));
                    protection.notifyExpelled(player, here);
                }
            }
        });
    }

    /** Boats and minecarts do not fire PlayerMoveEvent for their rider, so they get their own check. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleMove(VehicleMoveEvent event) {
        final Vehicle vehicle = event.getVehicle();
        Entity passenger = vehicle.getPassenger();
        if (!(passenger instanceof Player)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        final Player player = (Player) passenger;
        final Claim target = protection.claimAt(to);
        if (protection.canEnter(player, target)) {
            return;
        }
        Claim origin = protection.claimAt(from);
        final Location safe = (origin != null && !protection.canEnter(player, origin))
                ? protection.exitFor(player, origin, from)
                : from;
        // not inside the event: the entity is being ticked
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                vehicle.eject();
                vehicle.teleport(safe);
                player.teleport(safe);
                protection.notifyDenied(player, ProtectionService.MESSAGE_PRIVATE,
                        MessageService.placeholders("owner", target.getOwnerKey()));
            }
        });
    }
}
