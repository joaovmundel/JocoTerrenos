package io.github.joaovmundel.jocoTerrenos.protection;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ExitFinderTest {

    private static final Bounds LAND = new Bounds(0, 10, 0, 10);
    private static final int REACH = 16;

    private static final ExitFinder.Rule ANYWHERE = new ExitFinder.Rule() {
        @Override
        public boolean accepts(int x, int z) {
            return true;
        }
    };

    /** Flat ground at y=64 everywhere. */
    private static final ExitFinder.Ground FLAT = new ExitFinder.Ground() {
        @Override
        public int standableY(int x, int z, int referenceY) {
            return 64;
        }
    };

    private static ExitFinder.Spot find(int x, int z, ExitFinder.Rule rule, ExitFinder.Ground ground) {
        return ExitFinder.find(LAND, x, z, 64, REACH, rule, ground);
    }

    @Test
    public void leavesThroughTheNearestSideOneBlockOutside() {
        assertEquals(new ExitFinder.Spot(-1, 64, 5), find(1, 5, ANYWHERE, FLAT));
        assertEquals(new ExitFinder.Spot(11, 64, 5), find(9, 5, ANYWHERE, FLAT));
        assertEquals(new ExitFinder.Spot(5, 64, -1), find(5, 2, ANYWHERE, FLAT));
        assertEquals(new ExitFinder.Spot(5, 64, 11), find(5, 8, ANYWHERE, FLAT));
    }

    @Test
    public void aPlayerOnTheEdgeStepsStraightOut() {
        assertEquals(new ExitFinder.Spot(-1, 64, 5), find(0, 5, ANYWHERE, FLAT));
        assertEquals(new ExitFinder.Spot(11, 64, 5), find(10, 5, ANYWHERE, FLAT));
    }

    @Test
    public void theCenterPicksAStableSideAndStillLeaves() {
        ExitFinder.Spot spot = find(5, 5, ANYWHERE, FLAT);

        assertNotNull(spot);
        assertEquals(-1, spot.getX());
        assertEquals(5, spot.getZ());
    }

    @Test
    public void everySpotFoundIsOutsideTheLand() {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                ExitFinder.Spot spot = find(x, z, ANYWHERE, FLAT);
                assertNotNull(spot);
                assertTrue("(" + x + "," + z + ") -> " + spot, !LAND.contains(spot.getX(), spot.getZ()));
            }
        }
    }

    @Test
    public void skipsASideBlockedByAnotherLandAndUsesTheNext() {
        // a refusing land covers everything west of x=0
        ExitFinder.Rule noWest = new ExitFinder.Rule() {
            @Override
            public boolean accepts(int x, int z) {
                return x >= 0;
            }
        };

        ExitFinder.Spot spot = find(1, 5, noWest, FLAT);

        assertNotNull(spot);
        assertTrue(spot.getX() >= 0);
        assertTrue(!LAND.contains(spot.getX(), spot.getZ()));
    }

    @Test
    public void slidesAlongTheBorderWhenTheStraightColumnHasNoGround() {
        final Set<String> water = new HashSet<String>();
        water.add("-1,5");
        ExitFinder.Ground waterAtOneColumn = new ExitFinder.Ground() {
            @Override
            public int standableY(int x, int z, int referenceY) {
                return water.contains(x + "," + z) ? -1 : 64;
            }
        };

        ExitFinder.Spot spot = find(1, 5, ANYWHERE, waterAtOneColumn);

        // one block to either side of the flooded column is equally close
        assertEquals(-1, spot.getX());
        assertEquals(1, Math.abs(spot.getZ() - 5));
    }

    @Test
    public void goesFurtherOutWhenTheWholeFirstRingIsWater() {
        ExitFinder.Ground water = new ExitFinder.Ground() {
            @Override
            public int standableY(int x, int z, int referenceY) {
                return LAND.expand(1).contains(x, z) ? -1 : 64;
            }
        };

        ExitFinder.Spot spot = find(1, 5, ANYWHERE, water);

        assertEquals(new ExitFinder.Spot(-2, 64, 5), spot);
    }

    @Test
    public void usesTheHeightTheGroundReports() {
        ExitFinder.Ground hill = new ExitFinder.Ground() {
            @Override
            public int standableY(int x, int z, int referenceY) {
                return 70;
            }
        };

        assertEquals(70, find(1, 5, ANYWHERE, hill).getY());
    }

    @Test
    public void givesUpWhenNothingWithinReachIsAcceptable() {
        ExitFinder.Rule nowhere = new ExitFinder.Rule() {
            @Override
            public boolean accepts(int x, int z) {
                return false;
            }
        };

        assertNull(find(1, 5, nowhere, FLAT));
    }

    @Test
    public void givesUpWhenThereIsNoGroundAtAll() {
        ExitFinder.Ground void_ = new ExitFinder.Ground() {
            @Override
            public int standableY(int x, int z, int referenceY) {
                return -1;
            }
        };

        assertNull(find(1, 5, ANYWHERE, void_));
    }

    @Test
    public void aPlayerReportedOutsideTheBoundsIsTreatedAsOnTheNearestEdge() {
        ExitFinder.Spot spot = ExitFinder.find(LAND, -50, 5, 64, REACH, ANYWHERE, FLAT);

        assertEquals(new ExitFinder.Spot(-1, 64, 5), spot);
    }

    @Test
    public void theReferenceHeightIsPassedToTheGround() {
        final int[] seen = new int[1];
        ExitFinder.Ground recording = new ExitFinder.Ground() {
            @Override
            public int standableY(int x, int z, int referenceY) {
                seen[0] = referenceY;
                return 64;
            }
        };

        ExitFinder.find(LAND, 1, 5, 123, REACH, ANYWHERE, recording);

        assertEquals(123, seen[0]);
    }
}
