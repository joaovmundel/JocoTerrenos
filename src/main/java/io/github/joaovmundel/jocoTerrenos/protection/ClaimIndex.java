package io.github.joaovmundel.jocoTerrenos.protection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory spatial index of all claims, keyed by world and chunk, so block events can be answered without
 * touching the database. Safe to read from any thread: readers use an immutable snapshot that writers replace
 * wholesale (claims are few and change rarely, reads happen on every block event).
 */
public final class ClaimIndex {

    private static final int CHUNK_SHIFT = 4;

    private static final Snapshot EMPTY = new Snapshot(new HashMap<String, Map<Long, List<Claim>>>());

    private final Map<Long, Claim> claimsById = new LinkedHashMap<Long, Claim>();
    private volatile Snapshot snapshot = EMPTY;

    /** Replaces every claim; used on startup and after a reload from storage. */
    public synchronized void replaceAll(Collection<Claim> claims) {
        claimsById.clear();
        for (Claim claim : claims) {
            claimsById.put(claim.getId(), claim);
        }
        rebuild();
    }

    /** Adds the claim, or replaces the one with the same id. */
    public synchronized void put(Claim claim) {
        claimsById.put(claim.getId(), claim);
        rebuild();
    }

    public synchronized void remove(long id) {
        if (claimsById.remove(id) != null) {
            rebuild();
        }
    }

    /** @return the claim covering the block column, or {@code null} when the column is unclaimed */
    public Claim findAt(String world, int x, int z) {
        Map<Long, List<Claim>> chunks = snapshot.worlds.get(world);
        if (chunks == null) {
            return null;
        }
        List<Claim> candidates = chunks.get(chunkKey(x >> CHUNK_SHIFT, z >> CHUNK_SHIFT));
        if (candidates == null) {
            return null;
        }
        for (Claim claim : candidates) {
            if (claim.getBounds().contains(x, z)) {
                return claim;
            }
        }
        return null;
    }

    /**
     * @param buffer minimum number of free blocks required between {@code area} and any existing claim
     * @return whether {@code area} overlaps no claim and keeps at least {@code buffer} blocks from all of them
     */
    public boolean isAreaFree(String world, Bounds area, int buffer) {
        Map<Long, List<Claim>> chunks = snapshot.worlds.get(world);
        if (chunks == null) {
            return true;
        }
        Bounds probe = area.expand(Math.max(0, buffer));
        int minChunkX = probe.getMinX() >> CHUNK_SHIFT;
        int maxChunkX = probe.getMaxX() >> CHUNK_SHIFT;
        int minChunkZ = probe.getMinZ() >> CHUNK_SHIFT;
        int maxChunkZ = probe.getMaxZ() >> CHUNK_SHIFT;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                List<Claim> candidates = chunks.get(chunkKey(chunkX, chunkZ));
                if (candidates == null) {
                    continue;
                }
                for (Claim claim : candidates) {
                    if (claim.getBounds().intersects(probe)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public int size() {
        return snapshot.size;
    }

    private void rebuild() {
        Map<String, Map<Long, List<Claim>>> worlds = new HashMap<String, Map<Long, List<Claim>>>();
        for (Claim claim : claimsById.values()) {
            Map<Long, List<Claim>> chunks = worlds.get(claim.getWorld());
            if (chunks == null) {
                chunks = new HashMap<Long, List<Claim>>();
                worlds.put(claim.getWorld(), chunks);
            }
            Bounds bounds = claim.getBounds();
            for (int chunkX = bounds.getMinX() >> CHUNK_SHIFT; chunkX <= bounds.getMaxX() >> CHUNK_SHIFT; chunkX++) {
                for (int chunkZ = bounds.getMinZ() >> CHUNK_SHIFT; chunkZ <= bounds.getMaxZ() >> CHUNK_SHIFT; chunkZ++) {
                    long key = chunkKey(chunkX, chunkZ);
                    List<Claim> inChunk = chunks.get(key);
                    if (inChunk == null) {
                        inChunk = new ArrayList<Claim>(1);
                        chunks.put(key, inChunk);
                    }
                    inChunk.add(claim);
                }
            }
        }
        snapshot = new Snapshot(worlds, claimsById.size());
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /** Never mutated after construction, so readers need no locking. */
    private static final class Snapshot {
        private final Map<String, Map<Long, List<Claim>>> worlds;
        private final int size;

        private Snapshot(Map<String, Map<Long, List<Claim>>> worlds) {
            this(worlds, 0);
        }

        private Snapshot(Map<String, Map<Long, List<Claim>>> worlds, int size) {
            this.worlds = Collections.unmodifiableMap(worlds);
            this.size = size;
        }
    }
}
