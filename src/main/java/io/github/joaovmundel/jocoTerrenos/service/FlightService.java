package io.github.joaovmundel.jocoTerrenos.service;

import io.github.joaovmundel.jocoTerrenos.protection.Claim;
import io.github.joaovmundel.jocoTerrenos.protection.FlightRules;
import io.github.joaovmundel.jocoTerrenos.repositories.TerrenoRepository;
import io.github.joaovmundel.jocoTerrenos.utils.PlayerKeys;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /voar}: flight inside the player's own lands. It only manages players who switched it on with the
 * command, so flight granted by other plugins or by creative mode is never touched.
 * <p>
 * Flight is switched off the moment the player is somewhere it is not allowed (see {@link FlightRules}): when
 * they leave the land, teleport away, change world, lose the permission or die.
 */
public final class FlightService {

    /** Permission needed to use {@code /voar}. */
    public static final String PERMISSION = "jocoterrenos.voar";

    public static final String MESSAGE_OFF_LEFT_LAND = "fly.off-left-land";
    public static final String MESSAGE_OFF_WORLD = "fly.off-world";
    public static final String MESSAGE_OFF_PERMISSION = "fly.off-permission";

    /** After flight is cut off in mid-air the player ignores one fall for this long, so the plugin never kills. */
    private static final long FALL_PROTECTION_MILLIS = 10000L;

    /** What {@code /voar} did. */
    public enum ToggleResult {
        ENABLED, DISABLED, NO_PERMISSION, WORLD_NOT_ALLOWED, NOT_IN_OWN_LAND, CREATIVE_MODE
    }

    private final ProtectionService protection;
    private final TerrenoService terrenos;
    private final TerrenoRepository repository;
    private final MessageService messages;

    /** Names (as the server spells them) of the players flying through /voar right now. */
    private final Set<String> active = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Map<String, Long> fallProtectedUntil = new ConcurrentHashMap<String, Long>();
    /** Players the database says were flying when the server stopped without cleaning up (a crash). */
    private final Set<String> staleFlyers = new HashSet<String>();

    public FlightService(ProtectionService protection, TerrenoService terrenos, TerrenoRepository repository, MessageService messages) {
        this.protection = protection;
        this.terrenos = terrenos;
        this.repository = repository;
        this.messages = messages;
    }

    /**
     * Reads who was left flying by a previous run. Their "may fly" flag is still in their player file, so it is
     * cleared when they log in. Called once at startup, when nobody can be flying legitimately yet.
     */
    public void loadStaleFlyers() {
        staleFlyers.addAll(repository.loadFlyingPlayers());
    }

    public boolean isActive(Player player) {
        return active.contains(player.getName());
    }

    /** Whether the player could fly at that spot, and if not, the first reason why. */
    public FlightRules.Verdict verdictAt(Player player, Location where) {
        Claim claim = protection.claimAt(where);
        boolean worldAllowed = where.getWorld() != null && terrenos.mundoPermitido(where.getWorld().getName());
        return FlightRules.check(player.hasPermission(PERMISSION), worldAllowed, claim, PlayerKeys.of(player));
    }

    /** What {@code /voar} does: switches flight off if it is on, otherwise on when the player is allowed to. */
    public ToggleResult toggle(Player player) {
        if (isActive(player)) {
            disable(player);
            return ToggleResult.DISABLED;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            return ToggleResult.CREATIVE_MODE;
        }
        switch (verdictAt(player, player.getLocation())) {
            case NO_PERMISSION:
                return ToggleResult.NO_PERMISSION;
            case WORLD_NOT_ALLOWED:
                return ToggleResult.WORLD_NOT_ALLOWED;
            case NOT_IN_OWN_LAND:
                return ToggleResult.NOT_IN_OWN_LAND;
            default:
                enable(player);
                return ToggleResult.ENABLED;
        }
    }

    private void enable(Player player) {
        active.add(player.getName());
        repository.markFlying(PlayerKeys.of(player));
        player.setAllowFlight(true);
        if (!player.isOnGround()) {
            player.setFlying(true);
        }
    }

    /**
     * Switches flight off without a message, if it was on. The player keeps hovering for no longer than a tick,
     * and a fall right after is harmless for a few seconds.
     */
    public void disable(Player player) {
        if (!active.remove(player.getName())) {
            return;
        }
        repository.unmarkFlying(PlayerKeys.of(player));
        player.setFlying(false);
        player.setAllowFlight(false);
        fallProtectedUntil.put(PlayerKeys.of(player), System.currentTimeMillis() + FALL_PROTECTION_MILLIS);
    }

    /**
     * Checks a flying player against the spot they are at or going to, and switches flight off with an explanation
     * when it is no longer allowed there. Does nothing for players who are not flying.
     */
    public void revalidate(Player player, Location where) {
        if (!isActive(player)) {
            return;
        }
        switch (verdictAt(player, where)) {
            case ALLOWED:
                return;
            case NO_PERMISSION:
                disable(player);
                messages.send(player, MESSAGE_OFF_PERMISSION);
                return;
            case WORLD_NOT_ALLOWED:
                disable(player);
                messages.send(player, MESSAGE_OFF_WORLD);
                return;
            default:
                disable(player);
                messages.send(player, MESSAGE_OFF_LEFT_LAND);
        }
    }

    /** Re-checks every flying player where they are, e.g. after a land was deleted or sold. */
    public void revalidateAll() {
        if (active.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            revalidate(player, player.getLocation());
        }
    }

    /** Switches everybody's flight off, quietly. Used when the plugin stops. */
    public void disableAll() {
        List<String> names = new ArrayList<String>(active);
        for (String name : names) {
            Player player = Bukkit.getPlayerExact(name);
            if (player != null) {
                disable(player);
            } else {
                active.remove(name);
                repository.unmarkFlying(name.toLowerCase(Locale.ENGLISH));
            }
        }
    }

    /** At login: undoes the flight a crash left behind in the player file. */
    public void restoreAfterCrash(Player player) {
        String key = PlayerKeys.of(player);
        if (!staleFlyers.remove(key)) {
            return;
        }
        repository.unmarkFlying(key);
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    /**
     * @return whether a fall of this player should be ignored: flight was cut off in the last few seconds and the
     * grace has not been used yet. The grace is used up by the first call.
     */
    public boolean consumeFallProtection(Player player) {
        Long until = fallProtectedUntil.remove(PlayerKeys.of(player));
        return until != null && System.currentTimeMillis() <= until;
    }

    /** Forgets what is kept per player when they leave. */
    public void forget(Player player) {
        fallProtectedUntil.remove(PlayerKeys.of(player));
    }
}
