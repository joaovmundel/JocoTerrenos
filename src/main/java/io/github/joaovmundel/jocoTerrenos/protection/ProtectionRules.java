package io.github.joaovmundel.jocoTerrenos.protection;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Name based classification of materials and spawn reasons, using the Material names of Minecraft 1.5.2.
 * Working on names instead of Bukkit enums keeps the rules unit testable without a server.
 */
public final class ProtectionRules {

    /** Items whose right click acts on the world beyond the clicked block (liquids, fire, entities, tools). */
    private static final Set<String> RESTRICTED_ITEMS = new HashSet<String>(Arrays.asList(
            "BUCKET", "WATER_BUCKET", "LAVA_BUCKET", "FLINT_AND_STEEL", "FIREBALL", "MONSTER_EGG", "BOAT",
            "MINECART", "STORAGE_MINECART", "POWERED_MINECART", "EXPLOSIVE_MINECART", "HOPPER_MINECART",
            "WOOD_HOE", "STONE_HOE", "IRON_HOE", "GOLD_HOE", "DIAMOND_HOE",
            "INK_SACK", "PAINTING", "ITEM_FRAME", "EYE_OF_ENDER", "SHEARS"));

    /** Spawns that come from the world itself; spawn eggs, commands and golem/wither building are not here. */
    private static final Set<String> BLOCKED_SPAWN_REASONS = new HashSet<String>(Arrays.asList(
            "NATURAL", "CHUNK_GEN", "JOCKEY", "VILLAGE_INVASION", "LIGHTNING", "SPAWNER", "DEFAULT"));

    private ProtectionRules() {
    }

    public static boolean isRestrictedItem(String materialName) {
        return RESTRICTED_ITEMS.contains(materialName);
    }

    /** Whether a hostile mob spawning for this reason must be stopped on a land with mobs disabled. */
    public static boolean isBlockedSpawnReason(String reasonName) {
        return BLOCKED_SPAWN_REASONS.contains(reasonName);
    }
}
