package io.github.joaovmundel.jocoTerrenos.protection;

import io.github.joaovmundel.jocoTerrenos.models.TerrenoRole;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable snapshot of a land as the protection sees it. Player keys are opaque strings (lowercase player
 * names on 1.5.2); the caller must use the same representation for owners, members and the player being checked.
 */
public final class Claim {

    private final long id;
    private final String ownerKey;
    private final String world;
    private final Bounds bounds;
    private final boolean pvp;
    private final boolean mobs;
    private final boolean publicAccess;
    private final Map<String, TerrenoRole> members;

    public Claim(long id, String ownerKey, String world, Bounds bounds, boolean pvp, boolean mobs,
                 boolean publicAccess, Map<String, TerrenoRole> members) {
        if (ownerKey == null || world == null || bounds == null) {
            throw new IllegalArgumentException("ownerKey, world and bounds are required");
        }
        this.id = id;
        this.ownerKey = ownerKey;
        this.world = world;
        this.bounds = bounds;
        this.pvp = pvp;
        this.mobs = mobs;
        this.publicAccess = publicAccess;
        this.members = Collections.unmodifiableMap(new HashMap<String, TerrenoRole>(members));
    }

    /** Owner, ADMIN and MEMBER may build, break and open containers. */
    public boolean allowsBuild(String playerKey) {
        if (ownerKey.equals(playerKey)) {
            return true;
        }
        return members.containsKey(playerKey);
    }

    /** Everyone who can build, plus visitors when the land is public (doors, buttons, levers, gates). */
    public boolean allowsUse(String playerKey) {
        return publicAccess || allowsBuild(playerKey);
    }

    /**
     * Whether the player may stand inside the land: owner and members always, everyone else only while the
     * land is public. A private land keeps visitors out entirely, not just away from the blocks.
     */
    public boolean allowsEntry(String playerKey) {
        return publicAccess || allowsBuild(playerKey);
    }

    public long getId() {
        return id;
    }

    public String getOwnerKey() {
        return ownerKey;
    }

    public String getWorld() {
        return world;
    }

    public Bounds getBounds() {
        return bounds;
    }

    public boolean isPvp() {
        return pvp;
    }

    public boolean isMobs() {
        return mobs;
    }

    public boolean isPublicAccess() {
        return publicAccess;
    }
}
