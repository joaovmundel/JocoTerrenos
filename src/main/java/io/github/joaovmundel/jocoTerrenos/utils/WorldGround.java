package io.github.joaovmundel.jocoTerrenos.utils;

import io.github.joaovmundel.jocoTerrenos.protection.ExitFinder;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Looks for a spot where a player can stand, for {@link ExitFinder}: solid ground with two free blocks above it,
 * nothing harmful at the feet and not inside a liquid.
 */
public final class WorldGround implements ExitFinder.Ground {

    /** How far above and below the reference height to look before falling back to the surface. */
    private static final int HEIGHT_RANGE = 12;

    private final World world;

    public WorldGround(World world) {
        this.world = world;
    }

    @Override
    public int standableY(int x, int z, int referenceY) {
        int maxY = world.getMaxHeight() - 2;
        for (int distance = 0; distance <= HEIGHT_RANGE; distance++) {
            if (isStandableAt(x, referenceY + distance, z, maxY)) {
                return referenceY + distance;
            }
            if (distance > 0 && isStandableAt(x, referenceY - distance, z, maxY)) {
                return referenceY - distance;
            }
        }
        // The Nether has a bedrock roof: the highest block there is the roof, not where players walk.
        if (world.getEnvironment() != World.Environment.NETHER) {
            int surface = world.getHighestBlockYAt(x, z);
            if (isStandableAt(x, surface, z, maxY)) {
                return surface;
            }
        }
        return -1;
    }

    private boolean isStandableAt(int x, int y, int z, int maxY) {
        if (y < 1 || y > maxY) {
            return false;
        }
        return isStandable(
                world.getBlockAt(x, y - 1, z).getType(),
                world.getBlockAt(x, y, z).getType(),
                world.getBlockAt(x, y + 1, z).getType());
    }

    /**
     * @param ground the block under the player's feet
     * @param feet   the block the feet are in
     * @param head   the block above it
     */
    static boolean isStandable(Material ground, Material feet, Material head) {
        return ground.isSolid() && ground != Material.CACTUS && isFree(feet) && isFree(head);
    }

    /** Free to be in: not a solid block, and not a liquid, fire, web, portal or cactus. */
    private static boolean isFree(Material material) {
        if (material.isSolid()) {
            return false;
        }
        switch (material) {
            case WATER:
            case STATIONARY_WATER:
            case LAVA:
            case STATIONARY_LAVA:
            case FIRE:
            case WEB:
            case PORTAL:
            case ENDER_PORTAL:
            case CACTUS:
                return false;
            default:
                return true;
        }
    }
}
