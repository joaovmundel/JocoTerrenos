package io.github.joaovmundel.jocoTerrenos.listener;

import io.github.joaovmundel.jocoTerrenos.protection.Claim;
import io.github.joaovmundel.jocoTerrenos.protection.ProtectionRules;
import io.github.joaovmundel.jocoTerrenos.service.ProtectionService;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Applies the PvP and mobs flags and keeps non-hostile entities (animals, villagers, item frames, armor
 * stands) safe from players who cannot build on the land.
 */
public final class EntityProtectionListener implements Listener {

    private final ProtectionService protection;

    public EntityProtectionListener(ProtectionService protection) {
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = playerBehind(event.getDamager());
        if (attacker == null) {
            return;
        }
        Entity victim = event.getEntity();

        if (victim instanceof Player) {
            if (!victim.equals(attacker) && isPvpBlocked(attacker, victim)) {
                event.setCancelled(true);
                protection.notifyDenied(attacker, ProtectionService.MESSAGE_PVP_DISABLED);
            }
            return;
        }
        if (isHostile(victim)) {
            return;
        }
        if (protection.denyBuild(attacker, victim.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        Player player = event.getPlayer();
        if (isHostile(target) || target instanceof Player || isOwnPet(player, target)) {
            return;
        }
        if (protection.denyBuild(player, target.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (protection.denyBuild(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player remover = playerBehind(event.getRemover());
        if (remover == null) {
            // creepers, skeleton arrows, dispensers and the like never get to break frames and paintings on a land
            if (protection.claimAt(event.getEntity().getLocation()) != null) {
                event.setCancelled(true);
            }
            return;
        }
        if (protection.denyBuild(remover, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Hostile mobs spawning on a land that disabled them. Spawn eggs, commands and golem building still work. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isHostile(event.getEntity()) || !ProtectionRules.isBlockedSpawnReason(event.getSpawnReason().name())) {
            return;
        }
        Claim claim = protection.claimAt(event.getLocation());
        if (claim != null && !claim.isMobs()) {
            event.setCancelled(true);
        }
    }

    /** Endermen picking up blocks, zombies breaking doors, withers... Sheep eating grass is left alone. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof FallingBlock || !isHostile(entity)) {
            return;
        }
        if (protection.claimAt(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    private boolean isPvpBlocked(Player attacker, Entity victim) {
        if (protection.hasBypass(attacker)) {
            return false;
        }
        Claim atVictim = protection.claimAt(victim.getLocation());
        Claim atAttacker = protection.claimAt(attacker.getLocation());
        return (atVictim != null && !atVictim.isPvp()) || (atAttacker != null && !atAttacker.isPvp());
    }

    /** The player who dealt the damage, directly or through an arrow, trident, potion... */
    private static Player playerBehind(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }

    private static boolean isHostile(Entity entity) {
        return entity instanceof Enemy;
    }

    private static boolean isOwnPet(Player player, Entity entity) {
        return entity instanceof Tameable && player.equals(((Tameable) entity).getOwner());
    }
}
