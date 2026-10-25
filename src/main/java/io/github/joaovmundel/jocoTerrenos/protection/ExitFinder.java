package io.github.joaovmundel.jocoTerrenos.protection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the nearest place just outside a land where a player can be put down, for players who are inside a land
 * they may not enter (a home set before the land turned private, a login inside it, a teleport into it).
 * <p>
 * Kept free of Bukkit types: the world is reached through {@link Ground} and the "may stand here" question
 * through {@link Rule}, so the search can be unit tested on a fake world.
 */
public final class ExitFinder {

    /** How the surface of the world looks. */
    public interface Ground {
        /**
         * @param referenceY a height near the player, to prefer the floor they were on over a roof or a cave
         * @return a Y where a player can stand in that column, or a negative number when there is none
         */
        int standableY(int x, int z, int referenceY);
    }

    /** Whether the player may be placed in a column (it must not be inside another land that refuses them). */
    public interface Rule {
        boolean accepts(int x, int z);
    }

    /** A block position to put the player on. */
    public static final class Spot {
        private final int x;
        private final int y;
        private final int z;

        public Spot(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Spot)) {
                return false;
            }
            Spot that = (Spot) other;
            return x == that.x && y == that.y && z == that.z;
        }

        @Override
        public int hashCode() {
            return 31 * (31 * x + y) + z;
        }

        @Override
        public String toString() {
            return "Spot[" + x + ", " + y + ", " + z + "]";
        }
    }

    /** A column outside the land, with how far it is from the player. */
    private static final class Candidate {
        private final int x;
        private final int z;
        private final long squaredDistance;
        private final int order;

        private Candidate(int x, int z, long squaredDistance, int order) {
            this.x = x;
            this.z = z;
            this.squaredDistance = squaredDistance;
            this.order = order;
        }
    }

    /** Tried along the border when the straight-out column is blocked (water, a cliff, another land). */
    private static final int MAX_SHIFT = 3;

    private ExitFinder() {
    }

    /**
     * @param bounds      the land the player has to leave
     * @param fromX       where the player is (or wants to be)
     * @param maxDistance how many blocks beyond the border to look before giving up
     * @return the acceptable spot outside the land that is closest to the player, or {@code null} when none
     * within reach has ground and is accepted
     */
    public static Spot find(Bounds bounds, int fromX, int fromZ, int referenceY, int maxDistance, Rule rule, Ground ground) {
        int clampedX = clamp(fromX, bounds.getMinX(), bounds.getMaxX());
        int clampedZ = clamp(fromZ, bounds.getMinZ(), bounds.getMaxZ());

        for (Candidate candidate : candidates(bounds, clampedX, clampedZ, maxDistance)) {
            if (!rule.accepts(candidate.x, candidate.z)) {
                continue;
            }
            int y = ground.standableY(candidate.x, candidate.z, referenceY);
            if (y >= 0) {
                return new Spot(candidate.x, y, candidate.z);
            }
        }
        return null;
    }

    /**
     * Columns up to {@code maxDistance} blocks beyond each side (a few blocks to either side of the player's
     * own line as well), nearest to the player first. Ties keep west, east, north, south.
     */
    private static List<Candidate> candidates(Bounds bounds, int x, int z, int maxDistance) {
        List<Candidate> all = new ArrayList<Candidate>();
        int order = 0;
        for (int offset = 1; offset <= maxDistance; offset++) {
            for (int side = 0; side < 4; side++) {
                for (int shift = -MAX_SHIFT; shift <= MAX_SHIFT; shift++) {
                    int cx;
                    int cz;
                    switch (side) {
                        case 0:
                            cx = bounds.getMinX() - offset;
                            cz = z + shift;
                            break;
                        case 1:
                            cx = bounds.getMaxX() + offset;
                            cz = z + shift;
                            break;
                        case 2:
                            cx = x + shift;
                            cz = bounds.getMinZ() - offset;
                            break;
                        default:
                            cx = x + shift;
                            cz = bounds.getMaxZ() + offset;
                            break;
                    }
                    long dx = cx - x;
                    long dz = cz - z;
                    all.add(new Candidate(cx, cz, dx * dx + dz * dz, order++));
                }
            }
        }
        Collections.sort(all, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                if (a.squaredDistance != b.squaredDistance) {
                    return a.squaredDistance < b.squaredDistance ? -1 : 1;
                }
                return a.order < b.order ? -1 : (a.order == b.order ? 0 : 1);
            }
        });
        return all;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
