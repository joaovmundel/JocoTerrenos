package io.github.joaovmundel.jocoTerrenos.listener;

import io.github.joaovmundel.jocoTerrenos.protection.Claim;
import io.github.joaovmundel.jocoTerrenos.service.ProtectionService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;
import java.util.List;

/**
 * Keeps things that happen without a player (explosions, fire, flowing liquids, pistons) from damaging a
 * land. Nothing here is configurable per land: every claimed block is protected from the environment.
 */
public final class EnvironmentProtectionListener implements Listener {

    private static final long UNCLAIMED = -1L;

    private final ProtectionService protection;

    public EnvironmentProtectionListener(ProtectionService protection) {
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            if (protection.claimAt(iterator.next()) != null) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (protection.claimAt(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    /** Fire may only be lit on a land by someone who can build there; spread, lava and lightning never. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Claim claim = protection.claimAt(event.getBlock());
        if (claim == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null && protection.canBuild(player, claim)) {
            return;
        }
        event.setCancelled(true);
        if (player != null) {
            protection.notifyDeniedBuild(player, claim);
        }
    }

    /** A liquid may not flow into a land from outside it, nor from a different land. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Claim target = protection.claimAt(event.getToBlock());
        if (target == null) {
            return;
        }
        if (idOf(protection.claimAt(event.getBlock())) != target.getId()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        BlockFace direction = event.getDirection();
        List<Block> pushed = event.getBlocks();
        for (Block moved : pushed) {
            if (crossesLandBorder(moved, moved.getRelative(direction))) {
                event.setCancelled(true);
                return;
            }
        }
        // the head itself, and whatever it destroys when nothing is pushed
        if (crossesLandBorder(event.getBlock(), event.getBlock().getRelative(direction))) {
            event.setCancelled(true);
        }
    }

    /**
     * A sticky piston pulls the block two steps from it into the spot next to it. 1.5.2 only reports the
     * block being pulled, so the destination is worked out from the geometry instead of trusting
     * {@code getDirection()}.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!event.isSticky()) {
            return;
        }
        Block piston = event.getBlock();
        Block pulled = event.getRetractLocation().getBlock();
        if (pulled.getType() == Material.AIR) {
            return;
        }
        Block destination = piston.getRelative(
                Integer.signum(pulled.getX() - piston.getX()),
                Integer.signum(pulled.getY() - piston.getY()),
                Integer.signum(pulled.getZ() - piston.getZ()));
        if (crossesLandBorder(pulled, destination)) {
            event.setCancelled(true);
        }
    }

    /** Whether something moving from one block to the other would enter, leave or hop between lands. */
    private boolean crossesLandBorder(Block from, Block to) {
        return idOf(protection.claimAt(from)) != idOf(protection.claimAt(to));
    }

    private static long idOf(Claim claim) {
        return claim == null ? UNCLAIMED : claim.getId();
    }
}
