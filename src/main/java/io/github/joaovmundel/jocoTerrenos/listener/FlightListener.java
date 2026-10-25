package io.github.joaovmundel.jocoTerrenos.listener;

import io.github.joaovmundel.jocoTerrenos.service.FlightService;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Keeps {@code /voar} honest: every way a flying player can end up somewhere flight is not allowed, or stop being
 * the same session, switches it off. The checks run after the other listeners (MONITOR) so they see where the
 * player really ends up, e.g. after the entry protection moved them.
 */
public final class FlightListener implements Listener {

    private final FlightService flight;

    public FlightListener(FlightService flight) {
        this.flight = flight;
    }

    /** Runs on every step of every player, so it returns at once unless the player flies and changed block column. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!flight.isActive(player)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        flight.revalidate(player, to);
    }

    /** A teleport to another area (or world) ends the flight before the player arrives. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (flight.isActive(player) && event.getTo() != null) {
            flight.revalidate(player, event.getTo());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        flight.disable(event.getPlayer());
        flight.forget(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        flight.disable(event.getPlayer());
        flight.forget(event.getPlayer());
    }

    /** The "may fly" flag comes back off on respawn, so the plugin must not keep thinking the player flies. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        flight.disable(event.getEntity());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        flight.disable(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        flight.restoreAfterCrash(event.getPlayer());
    }

    /** One fall is forgiven after flight was cut off in mid-air. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.FALL
                && flight.consumeFallProtection((Player) entity)) {
            event.setCancelled(true);
        }
    }
}
