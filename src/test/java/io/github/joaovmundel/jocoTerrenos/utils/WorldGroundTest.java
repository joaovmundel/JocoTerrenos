package io.github.joaovmundel.jocoTerrenos.utils;

import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorldGroundTest {

    @Test
    public void solidGroundWithTwoFreeBlocksIsStandable() {
        assertTrue(WorldGround.isStandable(Material.GRASS, Material.AIR, Material.AIR));
        assertTrue(WorldGround.isStandable(Material.STONE, Material.AIR, Material.AIR));
    }

    @Test
    public void flowersAndTorchesAtTheFeetAreFine() {
        assertTrue(WorldGround.isStandable(Material.GRASS, Material.RED_ROSE, Material.AIR));
        assertTrue(WorldGround.isStandable(Material.GRASS, Material.LONG_GRASS, Material.AIR));
    }

    @Test
    public void thereMustBeSolidGroundUnderTheFeet() {
        assertFalse(WorldGround.isStandable(Material.AIR, Material.AIR, Material.AIR));
        assertFalse(WorldGround.isStandable(Material.WATER, Material.AIR, Material.AIR));
        assertFalse(WorldGround.isStandable(Material.LAVA, Material.AIR, Material.AIR));
    }

    @Test
    public void aCactusIsNotGround() {
        assertFalse(WorldGround.isStandable(Material.CACTUS, Material.AIR, Material.AIR));
    }

    @Test
    public void solidBlocksAtTheFeetOrHeadSuffocate() {
        assertFalse(WorldGround.isStandable(Material.GRASS, Material.STONE, Material.AIR));
        assertFalse(WorldGround.isStandable(Material.GRASS, Material.AIR, Material.STONE));
    }

    @Test
    public void liquidsFireAndWebsAreNotFreeSpace() {
        Material[] hazards = {Material.WATER, Material.STATIONARY_WATER, Material.LAVA, Material.STATIONARY_LAVA,
                Material.FIRE, Material.WEB, Material.PORTAL, Material.ENDER_PORTAL, Material.CACTUS};
        for (Material hazard : hazards) {
            assertFalse(hazard + " at the feet", WorldGround.isStandable(Material.GRASS, hazard, Material.AIR));
            assertFalse(hazard + " at the head", WorldGround.isStandable(Material.GRASS, Material.AIR, hazard));
        }
    }
}
