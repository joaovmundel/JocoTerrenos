package io.github.joaovmundel.jocoTerrenos.protection;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoundsTest {

    @Test
    public void oddSizeCoversExactlySizeBlocks() {
        Bounds bounds = Bounds.centeredOn(100, 200, 5);

        assertEquals(98, bounds.getMinX());
        assertEquals(102, bounds.getMaxX());
        assertEquals(198, bounds.getMinZ());
        assertEquals(202, bounds.getMaxZ());
    }

    @Test
    public void evenSizeIncludesTheFenceLine() {
        Bounds bounds = Bounds.centeredOn(100, 200, 10);

        assertEquals(95, bounds.getMinX());
        assertEquals(105, bounds.getMaxX());
        assertTrue(bounds.contains(95, 195));
        assertTrue(bounds.contains(105, 205));
        assertFalse(bounds.contains(94, 200));
        assertFalse(bounds.contains(106, 200));
    }

    @Test
    public void negativeCoordinatesAreHandled() {
        Bounds bounds = Bounds.centeredOn(-3, -7, 5);

        assertTrue(bounds.contains(-5, -9));
        assertTrue(bounds.contains(-1, -5));
        assertFalse(bounds.contains(0, -7));
    }

    @Test
    public void intersectsIsInclusiveOnTheEdges() {
        Bounds a = new Bounds(0, 10, 0, 10);

        assertTrue(a.intersects(new Bounds(10, 20, 0, 10)));
        assertFalse(a.intersects(new Bounds(11, 20, 0, 10)));
        assertFalse(a.intersects(new Bounds(0, 10, 11, 20)));
        assertTrue(a.intersects(new Bounds(3, 4, 3, 4)));
    }

    @Test
    public void expandGrowsEverySide() {
        Bounds expanded = new Bounds(0, 10, 0, 10).expand(3);

        assertEquals(new Bounds(-3, 13, -3, 13), expanded);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvertedRange() {
        new Bounds(5, 4, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveSize() {
        Bounds.centeredOn(0, 0, 0);
    }
}
