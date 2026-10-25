package io.github.joaovmundel.jocoTerrenos.protection;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProtectionRulesTest {

    @Test
    public void doorsGatesButtonsLeversAndPlatesAreUsableByVisitors() {
        assertTrue(ProtectionRules.isUsableBlock("OAK_DOOR"));
        assertTrue(ProtectionRules.isUsableBlock("SPRUCE_TRAPDOOR"));
        assertTrue(ProtectionRules.isUsableBlock("BIRCH_FENCE_GATE"));
        assertTrue(ProtectionRules.isUsableBlock("STONE_BUTTON"));
        assertTrue(ProtectionRules.isUsableBlock("LEVER"));
        assertTrue(ProtectionRules.isUsableBlock("STONE_PRESSURE_PLATE"));
    }

    @Test
    public void containersAndStationsAreNotUsableByVisitors() {
        assertFalse(ProtectionRules.isUsableBlock("CHEST"));
        assertFalse(ProtectionRules.isUsableBlock("TRAPPED_CHEST"));
        assertFalse(ProtectionRules.isUsableBlock("BARREL"));
        assertFalse(ProtectionRules.isUsableBlock("FURNACE"));
        assertFalse(ProtectionRules.isUsableBlock("CRAFTING_TABLE"));
        assertFalse(ProtectionRules.isUsableBlock("RED_BED"));
        assertFalse(ProtectionRules.isUsableBlock("STONE"));
    }

    @Test
    public void fenceItselfIsNotAGate() {
        assertFalse(ProtectionRules.isUsableBlock("OAK_FENCE"));
    }

    @Test
    public void itemsThatChangeTheWorldAreRestricted() {
        assertTrue(ProtectionRules.isRestrictedItem("WATER_BUCKET"));
        assertTrue(ProtectionRules.isRestrictedItem("LAVA_BUCKET"));
        assertTrue(ProtectionRules.isRestrictedItem("FLINT_AND_STEEL"));
        assertTrue(ProtectionRules.isRestrictedItem("ZOMBIE_SPAWN_EGG"));
        assertTrue(ProtectionRules.isRestrictedItem("OAK_BOAT"));
        assertTrue(ProtectionRules.isRestrictedItem("HOPPER_MINECART"));
        assertTrue(ProtectionRules.isRestrictedItem("MINECART"));
        assertTrue(ProtectionRules.isRestrictedItem("DIAMOND_HOE"));
        assertTrue(ProtectionRules.isRestrictedItem("NETHERITE_AXE"));
        assertTrue(ProtectionRules.isRestrictedItem("BONE_MEAL"));
        assertTrue(ProtectionRules.isRestrictedItem("ARMOR_STAND"));
    }

    @Test
    public void everydayItemsAreNotRestricted() {
        assertFalse(ProtectionRules.isRestrictedItem("BREAD"));
        assertFalse(ProtectionRules.isRestrictedItem("DIAMOND_SWORD"));
        assertFalse(ProtectionRules.isRestrictedItem("BOW"));
        assertFalse(ProtectionRules.isRestrictedItem("ENDER_PEARL"));
        assertFalse(ProtectionRules.isRestrictedItem("DIAMOND_PICKAXE"));
        assertFalse(ProtectionRules.isRestrictedItem("STONE"));
    }

    @Test
    public void worldDrivenSpawnsAreBlockedButPlayerDrivenOnesAreNot() {
        assertTrue(ProtectionRules.isBlockedSpawnReason("NATURAL"));
        assertTrue(ProtectionRules.isBlockedSpawnReason("SPAWNER"));
        assertTrue(ProtectionRules.isBlockedSpawnReason("REINFORCEMENTS"));
        assertFalse(ProtectionRules.isBlockedSpawnReason("SPAWNER_EGG"));
        assertFalse(ProtectionRules.isBlockedSpawnReason("CUSTOM"));
        assertFalse(ProtectionRules.isBlockedSpawnReason("BUILD_WITHER"));
        assertFalse(ProtectionRules.isBlockedSpawnReason("BREEDING"));
    }
}
