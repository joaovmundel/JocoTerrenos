package io.github.joaovmundel.jocoTerrenos.protection;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AllowedWorldsTest {

    @Test
    public void anEmptyListAllowsEveryWorld() {
        AllowedWorlds worlds = new AllowedWorlds(Collections.<String>emptyList());

        assertFalse(worlds.isRestricted());
        assertTrue(worlds.allows("world"));
        assertTrue(worlds.allows("world_nether"));
        assertEquals("", worlds.describe());
    }

    @Test
    public void onlyListedWorldsAreAllowed() {
        AllowedWorlds worlds = new AllowedWorlds(Arrays.asList("world", "plano"));

        assertTrue(worlds.isRestricted());
        assertTrue(worlds.allows("world"));
        assertTrue(worlds.allows("plano"));
        assertFalse(worlds.allows("world_nether"));
        assertFalse(worlds.allows("world_the_end"));
    }

    @Test
    public void namesAreComparedIgnoringCase() {
        AllowedWorlds worlds = new AllowedWorlds(Arrays.asList("World", " PLANO "));

        assertTrue(worlds.allows("world"));
        assertTrue(worlds.allows("WORLD"));
        assertTrue(worlds.allows("plano"));
    }

    @Test
    public void blankAndNullEntriesAreIgnoredAndDoNotRestrictByThemselves() {
        AllowedWorlds worlds = new AllowedWorlds(Arrays.asList("", "   ", null));

        assertFalse(worlds.isRestricted());
        assertTrue(worlds.allows("anything"));
    }

    @Test
    public void describeListsTheWorldsOnceAsTyped() {
        AllowedWorlds worlds = new AllowedWorlds(Arrays.asList("world", "Plano", "WORLD"));

        assertEquals("world, Plano", worlds.describe());
    }

    @Test
    public void aSimilarNameIsNotTheSameWorld() {
        AllowedWorlds worlds = new AllowedWorlds(Collections.singletonList("world"));

        assertFalse(worlds.allows("world2"));
        assertFalse(worlds.allows("my_world"));
    }
}
