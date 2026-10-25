package io.github.joaovmundel.jocoTerrenos.protection;

import io.github.joaovmundel.jocoTerrenos.models.TerrenoRole;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ClaimIndexTest {

    private ClaimIndex index;

    private static Claim claim(long id, String world, Bounds bounds) {
        return new Claim(id, "owner-" + id, world, bounds, false, true, false, Collections.<String, TerrenoRole>emptyMap());
    }

    @Before
    public void setUp() {
        index = new ClaimIndex();
    }

    @Test
    public void findsTheClaimCoveringAColumn() {
        Claim claim = claim(1, "world", Bounds.centeredOn(100, 100, 10));
        index.put(claim);

        assertSame(claim, index.findAt("world", 100, 100));
        assertSame(claim, index.findAt("world", 95, 105));
        assertNull(index.findAt("world", 94, 100));
        assertNull(index.findAt("world", 106, 100));
    }

    @Test
    public void worldsAreIsolated() {
        index.put(claim(1, "world", new Bounds(0, 10, 0, 10)));

        assertNull(index.findAt("world_nether", 5, 5));
    }

    @Test
    public void findsClaimsSpanningSeveralChunks() {
        Claim wide = claim(1, "world", new Bounds(-40, 40, -40, 40));
        index.put(wide);

        assertSame(wide, index.findAt("world", -40, -40));
        assertSame(wide, index.findAt("world", 40, 40));
        assertSame(wide, index.findAt("world", 0, 0));
        assertNull(index.findAt("world", 41, 0));
    }

    @Test
    public void negativeChunksDoNotCollideWithPositiveOnes() {
        Claim negative = claim(1, "world", new Bounds(-20, -10, -20, -10));
        index.put(negative);

        assertSame(negative, index.findAt("world", -15, -15));
        assertNull(index.findAt("world", 15, 15));
        assertNull(index.findAt("world", -15, 15));
    }

    @Test
    public void putWithSameIdReplacesTheClaim() {
        index.put(claim(1, "world", new Bounds(0, 10, 0, 10)));
        Claim moved = claim(1, "world", new Bounds(100, 110, 100, 110));
        index.put(moved);

        assertEquals(1, index.size());
        assertNull(index.findAt("world", 5, 5));
        assertSame(moved, index.findAt("world", 105, 105));
    }

    @Test
    public void removeForgetsTheClaim() {
        index.put(claim(1, "world", new Bounds(0, 10, 0, 10)));

        index.remove(1);

        assertEquals(0, index.size());
        assertNull(index.findAt("world", 5, 5));
    }

    @Test
    public void removingAnUnknownIdIsHarmless() {
        index.put(claim(1, "world", new Bounds(0, 10, 0, 10)));

        index.remove(99);

        assertEquals(1, index.size());
    }

    @Test
    public void replaceAllDropsEverythingNotListed() {
        index.put(claim(1, "world", new Bounds(0, 10, 0, 10)));

        index.replaceAll(Arrays.asList(claim(2, "world", new Bounds(50, 60, 50, 60))));

        assertEquals(1, index.size());
        assertNull(index.findAt("world", 5, 5));
        assertEquals(2L, index.findAt("world", 55, 55).getId());
    }

    @Test
    public void areaOverlappingAClaimIsNotFree() {
        index.put(claim(1, "world", new Bounds(0, 10, 0, 10)));

        assertFalse(index.isAreaFree("world", new Bounds(10, 20, 10, 20), 0));
        assertFalse(index.isAreaFree("world", new Bounds(2, 4, 2, 4), 0));
    }

    @Test
    public void bufferMeansMinimumFreeBlocksBetweenClaims() {
        index.put(claim(1, "world", new Bounds(0, 10, 0, 10)));

        // columns 11, 12 and 13 stay free: exactly 3 blocks of distance
        assertTrue(index.isAreaFree("world", new Bounds(14, 20, 0, 10), 3));
        // only columns 11 and 12 are free: 2 blocks, not enough
        assertFalse(index.isAreaFree("world", new Bounds(13, 20, 0, 10), 3));
    }

    @Test
    public void zeroBufferAllowsTouchingNeighbours() {
        index.put(claim(1, "world", new Bounds(0, 10, 0, 10)));

        assertTrue(index.isAreaFree("world", new Bounds(11, 20, 0, 10), 0));
    }

    @Test
    public void bufferAlsoCountsAcrossChunkBorders() {
        index.put(claim(1, "world", new Bounds(0, 15, 0, 15)));

        // the area starts in chunk 1; only the buffer makes the probe reach the claim in chunk 0
        assertFalse(index.isAreaFree("world", new Bounds(18, 30, 0, 15), 3));
        assertTrue(index.isAreaFree("world", new Bounds(19, 30, 0, 15), 3));
    }

    @Test
    public void areaInAnotherWorldIsFree() {
        index.put(claim(1, "world", new Bounds(0, 10, 0, 10)));

        assertTrue(index.isAreaFree("world_nether", new Bounds(0, 10, 0, 10), 3));
    }

    @Test
    public void emptyIndexHasEverythingFree() {
        assertTrue(index.isAreaFree("world", new Bounds(0, 10, 0, 10), 3));
        assertNull(index.findAt("world", 0, 0));
    }
}
