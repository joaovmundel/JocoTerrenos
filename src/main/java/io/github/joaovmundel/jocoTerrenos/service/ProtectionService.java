package io.github.joaovmundel.jocoTerrenos.service;

import io.github.joaovmundel.jocoTerrenos.protection.Claim;
import io.github.joaovmundel.jocoTerrenos.protection.ClaimIndex;
import io.github.joaovmundel.jocoTerrenos.utils.PlayerKeys;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers "may this player touch that spot?" for the listeners and tells the player when the answer is no.
 * Players are identified by lowercase name, the same key the lands are stored with.
 */
public final class ProtectionService {

    /** Permission that skips every land check (staff). */
    public static final String BYPASS_PERMISSION = "jocoterrenos.bypass";

    public static final String MESSAGE_DENIED = "protection.denied";
    public static final String MESSAGE_PVP_DISABLED = "protection.pvp-disabled";

    private final ClaimIndex claimIndex;
    private final MessageService messages;
    private final long messageCooldownMillis;
    private final Map<String, Long> lastMessageAt = new ConcurrentHashMap<String, Long>();

    public ProtectionService(ClaimIndex claimIndex, MessageService messages, long messageCooldownMillis) {
        this.claimIndex = claimIndex;
        this.messages = messages;
        this.messageCooldownMillis = messageCooldownMillis;
    }

    /** @return the land covering the location, or {@code null} when it is unclaimed */
    public Claim claimAt(Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        return claimIndex.findAt(location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
    }

    /** @return the land covering the block, or {@code null} when it is unclaimed */
    public Claim claimAt(Block block) {
        return claimIndex.findAt(block.getWorld().getName(), block.getX(), block.getZ());
    }

    public boolean hasBypass(Player player) {
        return player.hasPermission(BYPASS_PERMISSION);
    }

    /** @param claim the land, or {@code null} for unclaimed ground (always allowed) */
    public boolean canBuild(Player player, Claim claim) {
        return claim == null || claim.allowsBuild(PlayerKeys.of(player)) || hasBypass(player);
    }

    /** @param claim the land, or {@code null} for unclaimed ground (always allowed) */
    public boolean canUse(Player player, Claim claim) {
        return claim == null || claim.allowsUse(PlayerKeys.of(player)) || hasBypass(player);
    }

    /**
     * @return {@code true} when the player may not build at the block, after telling them so; the caller
     * only has to cancel its event
     */
    public boolean denyBuild(Player player, Block block) {
        Claim claim = claimAt(block);
        if (canBuild(player, claim)) {
            return false;
        }
        notifyDeniedBuild(player, claim);
        return true;
    }

    /** Same as {@link #denyBuild(Player, Block)} for a location (entities, hanging items). */
    public boolean denyBuild(Player player, Location location) {
        Claim claim = claimAt(location);
        if (canBuild(player, claim)) {
            return false;
        }
        notifyDeniedBuild(player, claim);
        return true;
    }

    /** Tells the player the land belongs to someone else. */
    public void notifyDeniedBuild(Player player, Claim claim) {
        notifyDenied(player, MESSAGE_DENIED, MessageService.placeholders("owner", claim.getOwnerKey()));
    }

    public void notifyDenied(Player player, String messageKey) {
        notifyDenied(player, messageKey, Collections.<String, Object>emptyMap());
    }

    /**
     * Tells the player why they were stopped. Events like block interactions fire many times per second, so
     * the message is rate limited per player.
     */
    public void notifyDenied(Player player, String messageKey, Map<String, ?> placeholders) {
        String key = PlayerKeys.of(player);
        long now = System.currentTimeMillis();
        Long last = lastMessageAt.get(key);
        if (last != null && now - last < messageCooldownMillis) {
            return;
        }
        lastMessageAt.put(key, now);
        messages.send(player, messageKey, placeholders);
    }

    /** Drops the rate limit state of a player who left. */
    public void forget(Player player) {
        lastMessageAt.remove(PlayerKeys.of(player));
    }
}
