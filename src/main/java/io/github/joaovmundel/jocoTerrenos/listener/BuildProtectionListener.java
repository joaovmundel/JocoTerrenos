package io.github.joaovmundel.jocoTerrenos.listener;

import io.github.joaovmundel.jocoTerrenos.protection.Claim;
import io.github.joaovmundel.jocoTerrenos.protection.ProtectionRules;
import io.github.joaovmundel.jocoTerrenos.service.ProtectionService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Stops players from changing or operating blocks inside lands they cannot build on. It only translates
 * events into questions for {@link ProtectionService}.
 */
public final class BuildProtectionListener implements Listener {

    private final ProtectionService protection;

    public BuildProtectionListener(ProtectionService protection) {
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (protection.denyBuild(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (protection.denyBuild(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (protection.denyBuild(event.getPlayer(), target)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (protection.denyBuild(event.getPlayer(), event.getBlockClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && protection.denyBuild(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Action action = event.getAction();
        if (block == null || (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL)) {
            return;
        }
        Player player = event.getPlayer();
        Claim claim = protection.claimAt(block);
        if (claim == null || protection.canBuild(player, claim)) {
            return;
        }

        if (action == Action.PHYSICAL) {
            handlePhysical(event, player, claim, block);
            return;
        }
        if (ProtectionRules.isUsableBlock(block.getType().name()) && protection.canUse(player, claim)) {
            return;
        }

        event.setUseInteractedBlock(Event.Result.DENY);
        ItemStack item = event.getItem();
        if (item != null && ProtectionRules.isRestrictedItem(item.getType().name())) {
            event.setUseItemInHand(Event.Result.DENY);
        }
        protection.notifyDenied(player, ProtectionService.MESSAGE_DENIED);
    }

    /** Stepping on something: plates and tripwires follow the "use" rule, trampling farmland needs build access. */
    private void handlePhysical(PlayerInteractEvent event, Player player, Claim claim, Block block) {
        boolean trampling = block.getType() == Material.SOIL;
        if (!trampling && protection.canUse(player, claim)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        protection.forget(event.getPlayer());
    }
}
