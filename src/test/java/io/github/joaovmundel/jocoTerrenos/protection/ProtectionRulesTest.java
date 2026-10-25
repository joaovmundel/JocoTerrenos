package io.github.joaovmundel.jocoTerrenos.protection;

import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProtectionRulesTest {

    @Test
    public void doorsGatesButtonsLeversAndPlatesAreUsableByVisitors() {
        assertTrue(ProtectionRules.isUsableBlock("WOODEN_DOOR"));
        assertTrue(ProtectionRules.isUsableBlock("TRAP_DOOR"));
        assertTrue(ProtectionRules.isUsableBlock("FENCE_GATE"));
        assertTrue(ProtectionRules.isUsableBlock("STONE_BUTTON"));
        assertTrue(ProtectionRules.isUsableBlock("WOOD_BUTTON"));
        assertTrue(ProtectionRules.isUsableBlock("LEVER"));
        assertTrue(ProtectionRules.isUsableBlock("STONE_PLATE"));
        assertTrue(ProtectionRules.isUsableBlock("IRON_PLATE"));
    }

    @Test
    public void containersAndStationsAreNotUsableByVisitors() {
        assertFalse(ProtectionRules.isUsableBlock("CHEST"));
        assertFalse(ProtectionRules.isUsableBlock("TRAPPED_CHEST"));
        assertFalse(ProtectionRules.isUsableBlock("FURNACE"));
        assertFalse(ProtectionRules.isUsableBlock("WORKBENCH"));
        assertFalse(ProtectionRules.isUsableBlock("BED_BLOCK"));
        assertFalse(ProtectionRules.isUsableBlock("STONE"));
    }

    @Test
    public void fenceItselfIsNotAGate() {
        assertFalse(ProtectionRules.isUsableBlock("FENCE"));
    }

    @Test
    public void itemsThatChangeTheWorldAreRestricted() {
        assertTrue(ProtectionRules.isRestrictedItem("WATER_BUCKET"));
        assertTrue(ProtectionRules.isRestrictedItem("LAVA_BUCKET"));
        assertTrue(ProtectionRules.isRestrictedItem("BUCKET"));
        assertTrue(ProtectionRules.isRestrictedItem("FLINT_AND_STEEL"));
        assertTrue(ProtectionRules.isRestrictedItem("FIREBALL"));
        assertTrue(ProtectionRules.isRestrictedItem("MONSTER_EGG"));
        assertTrue(ProtectionRules.isRestrictedItem("BOAT"));
        assertTrue(ProtectionRules.isRestrictedItem("HOPPER_MINECART"));
        assertTrue(ProtectionRules.isRestrictedItem("MINECART"));
        assertTrue(ProtectionRules.isRestrictedItem("DIAMOND_HOE"));
        assertTrue(ProtectionRules.isRestrictedItem("INK_SACK"));
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
        assertFalse(ProtectionRules.isBlockedSpawnReason("SPAWNER_EGG"));
        assertFalse(ProtectionRules.isBlockedSpawnReason("CUSTOM"));
        assertFalse(ProtectionRules.isBlockedSpawnReason("BUILD_WITHER"));
        assertFalse(ProtectionRules.isBlockedSpawnReason("BREEDING"));
    }

    /** Every name the rules rely on must exist in the 1.5.2 API; a typo would silently protect nothing. */
    @Test
    public void everyNamedMaterialExistsInTheApi() {
        String[] names = {
                "WOODEN_DOOR", "IRON_DOOR_BLOCK", "TRAP_DOOR", "FENCE_GATE", "LEVER", "STONE_BUTTON", "WOOD_BUTTON",
                "STONE_PLATE", "WOOD_PLATE", "GOLD_PLATE", "IRON_PLATE", "TRIPWIRE", "TRIPWIRE_HOOK",
                "BUCKET", "WATER_BUCKET", "LAVA_BUCKET", "FLINT_AND_STEEL", "FIREBALL", "MONSTER_EGG", "BOAT",
                "MINECART", "STORAGE_MINECART", "POWERED_MINECART", "EXPLOSIVE_MINECART", "HOPPER_MINECART",
                "WOOD_HOE", "STONE_HOE", "IRON_HOE", "GOLD_HOE", "DIAMOND_HOE",
                "INK_SACK", "PAINTING", "ITEM_FRAME", "EYE_OF_ENDER", "SHEARS", "SOIL", "FENCE", "NETHER_FENCE"};
        for (String name : names) {
            Material.valueOf(name);
        }
    }
}
