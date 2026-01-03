package io.github.joaovmundel.jocoTerrenos.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;

public final class SafeLocationUtils {
    private SafeLocationUtils() {
    }

    public static Location findSafeSpot(Location center, int size, FileConfiguration config) {
        if (center == null || center.getWorld() == null || size <= 0) return null;
        World world = center.getWorld();
        int half = size / 2;
        int minY = config.getInt("lands.teleport.min-y", config.getInt("terrenos.teleport.altura-min", world.getMinHeight()));
        int maxY = config.getInt("lands.teleport.max-y", config.getInt("terrenos.teleport.altura-max", world.getMaxHeight()));

        // Try center first
        Location candidate = surfaceAt(world, center.getBlockX(), center.getBlockZ(), minY, maxY);
        if (isSafe(candidate)) return candidate.add(0.5, 0, 0.5);

        // Single retry with small offset within area bounds
        int offset = Math.min(2, Math.max(1, half - 1));
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int[][] offsets = new int[][]{
                {offset, offset}, {-offset, offset}, {offset, -offset}, {-offset, -offset}
        };
        for (int[] off : offsets) {
            int x = clampToArea(cx + off[0], cx - half, cx + half);
            int z = clampToArea(cz + off[1], cz - half, cz + half);
            Location loc = surfaceAt(world, x, z, minY, maxY);
            if (isSafe(loc)) return loc.add(0.5, 0, 0.5);
        }

        // Fallbacks
        if (isVoidWorld(world)) {
            return ensureVoidPad(center);
        }
        // Island case: try to find ground below center limited depth
        Location groundBelow = findGroundBelow(world, center.getBlockX(), center.getBlockZ(), minY);
        if (isSafe(groundBelow)) return groundBelow.add(0.5, 0, 0.5);

        return null;
    }

    private static Location surfaceAt(World world, int x, int z, int minY, int maxY) {
        // Scan from top down to find first solid block with 2 air blocks above
        for (int y = Math.min(maxY, world.getMaxHeight() - 1); y >= Math.max(minY, world.getMinHeight()); y--) {
            Block base = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            Block aboveHead = world.getBlockAt(x, y + 2, z);
            if (base.getType().isSolid() && isAirLike(head.getType()) && isAirLike(aboveHead.getType())) {
                // Player stands on y+1
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }

    private static Location findGroundBelow(World world, int x, int z, int minY) {
        // Scan downwards to first solid block, then place player on top
        for (int y = world.getMaxHeight() - 1; y >= Math.max(minY, world.getMinHeight()); y--) {
            Block b = world.getBlockAt(x, y, z);
            if (b.getType().isSolid()) {
                Block head = world.getBlockAt(x, y + 1, z);
                Block aboveHead = world.getBlockAt(x, y + 2, z);
                if (isAirLike(head.getType()) && isAirLike(aboveHead.getType())) {
                    return new Location(world, x + 0.5, y + 1, z + 0.5);
                }
            }
        }
        return null;
    }

    private static boolean isSafe(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        World world = loc.getWorld();
        Block feet = world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        Block head = world.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());
        Block ground = world.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
        // Avoid suffocation and ensure solid ground
        return isAirLike(feet.getType()) && isAirLike(head.getType()) && ground.getType().isSolid();
    }

    private static boolean isAirLike(Material m) {
        return m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR;
    }

    private static boolean isVoidWorld(World world) {
        // Heuristic: if min height is very low and there is no ground under center up to a reasonable depth
        return "the_end".equalsIgnoreCase(world.getEnvironment().name()) || world.getEnvironment() == World.Environment.THE_END;
    }

    public static Location ensureVoidPad(Location center) {
        World world = center.getWorld();
        if (world == null) return null;
        Material mat = Material.GLASS;
        int y = Math.max(center.getBlockY(), world.getMinHeight() + 1);
        int x = center.getBlockX();
        int z = center.getBlockZ();
        Block ground = world.getBlockAt(x, y - 1, z);
        if (!ground.getType().isSolid()) {
            ground.setType(mat);
        }
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        feet.setType(Material.AIR);
        head.setType(Material.AIR);
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private static int clampToArea(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}

