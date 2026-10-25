package io.github.joaovmundel.jocoTerrenos.listener;

import io.github.joaovmundel.jocoTerrenos.protection.Claim;
import io.github.joaovmundel.jocoTerrenos.service.ProtectionService;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
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
        removeClaimed(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeClaimed(event.blockList());
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
            protection.notifyDenied(player, ProtectionService.MESSAGE_DENIED);
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
        for (Block moved : event.getBlocks()) {
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        List<Block> blocks = event.getBlocks();
        if (blocks.isEmpty()) {
            return;
        }
        BlockFace pull = pullDirection(event.getBlock(), blocks.get(0), event.getDirection());
        for (Block moved : blocks) {
            if (crossesLandBorder(moved, moved.getRelative(pull))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Pulled blocks move toward the piston. Whether {@code getDirection()} is the face the piston looks at or
     * the way the blocks travel is not something to rely on, so pick the face of that axis that brings the
     * block closer to the piston.
     */
    private static BlockFace pullDirection(Block piston, Block pulled, BlockFace axis) {
        int current = manhattanDistance(piston, pulled);
        int along = manhattanDistance(piston, pulled.getRelative(axis));
        return along < current ? axis : axis.getOppositeFace();
    }

    private static int manhattanDistance(Block a, Block b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private void removeClaimed(List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            if (protection.claimAt(iterator.next()) != null) {
                iterator.remove();
            }
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
