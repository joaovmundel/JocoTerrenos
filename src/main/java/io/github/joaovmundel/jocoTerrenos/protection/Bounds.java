package io.github.joaovmundel.jocoTerrenos.protection;

/**
 * Inclusive rectangle of block columns (X/Z) in a single world. Immutable.
 * <p>
 * Kept free of Bukkit types so the protection rules can be unit tested without a server.
 */
public final class Bounds {

    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;

    public Bounds(int minX, int maxX, int minZ, int maxZ) {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("min must not exceed max: x=" + minX + ".." + maxX + ", z=" + minZ + ".." + maxZ);
        }
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    /**
     * Area of a land bought at the given block, with the same geometry the fences use: the ring of fences sits
     * {@code size / 2} blocks away from the center, so for even sizes the claim (fence line included) is one
     * block wider than {@code size}.
     */
    public static Bounds centeredOn(int blockX, int blockZ, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
        int radius = size / 2;
        return new Bounds(blockX - radius, blockX + radius, blockZ - radius, blockZ + radius);
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean intersects(Bounds other) {
        return minX <= other.maxX && maxX >= other.minX && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    /** @return a copy grown by {@code amount} blocks on every side */
    public Bounds expand(int amount) {
        return new Bounds(minX - amount, maxX + amount, minZ - amount, maxZ + amount);
    }

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxZ() {
        return maxZ;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Bounds)) {
            return false;
        }
        Bounds that = (Bounds) other;
        return minX == that.minX && maxX == that.maxX && minZ == that.minZ && maxZ == that.maxZ;
    }

    @Override
    public int hashCode() {
        int result = minX;
        result = 31 * result + maxX;
        result = 31 * result + minZ;
        result = 31 * result + maxZ;
        return result;
    }

    @Override
    public String toString() {
        return "Bounds[x=" + minX + ".." + maxX + ", z=" + minZ + ".." + maxZ + "]";
    }
}
