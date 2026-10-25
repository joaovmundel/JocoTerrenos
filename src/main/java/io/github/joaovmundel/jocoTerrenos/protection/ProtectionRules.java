package io.github.joaovmundel.jocoTerrenos.protection;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Name based classification of materials and spawn reasons. Working on names instead of Bukkit enums keeps
 * the rules unit testable without a server, and a name that does not exist in some game version is harmless.
 */
public final class ProtectionRules {

    private static final String[] USABLE_SUFFIXES = {"_DOOR", "_TRAPDOOR", "_FENCE_GATE", "_BUTTON", "_PRESSURE_PLATE"};
    private static final String LEVER = "LEVER";

    private static final String[] RESTRICTED_ITEM_SUFFIXES = {
            "_BUCKET", "_SPAWN_EGG", "_BOAT", "_RAFT", "_MINECART", "_HOE", "_SHOVEL", "_AXE"};
    private static final Set<String> RESTRICTED_ITEMS = new HashSet<String>(Arrays.asList(
            "FLINT_AND_STEEL", "FIRE_CHARGE", "ARMOR_STAND", "ITEM_FRAME", "GLOW_ITEM_FRAME", "PAINTING",
            "END_CRYSTAL", "MINECART", "BONE_MEAL", "HONEYCOMB", "SHEARS", "LEAD"));

    /** Spawns that come from the world itself; spawn eggs, commands and golem/wither building are not here. */
    private static final Set<String> BLOCKED_SPAWN_REASONS = new HashSet<String>(Arrays.asList(
            "NATURAL", "CHUNK_GEN", "JOCKEY", "REINFORCEMENTS", "VILLAGE_INVASION", "RAID", "PATROL",
            "LIGHTNING", "NETHER_PORTAL", "TRAP", "SPAWNER", "DEFAULT"));

    private ProtectionRules() {
    }

    /**
     * Blocks a visitor may operate on a public land: doors, trapdoors, gates, buttons, levers and plates.
     * Everything else (containers, crafting, beds, redstone components...) needs build access.
     */
    public static boolean isUsableBlock(String materialName) {
        if (LEVER.equals(materialName)) {
            return true;
        }
        return endsWithAny(materialName, USABLE_SUFFIXES);
    }

    /** Items whose right click acts on the world beyond the clicked block (liquids, fire, entities, tools). */
    public static boolean isRestrictedItem(String materialName) {
        return RESTRICTED_ITEMS.contains(materialName) || endsWithAny(materialName, RESTRICTED_ITEM_SUFFIXES);
    }

    /** Whether a hostile mob spawning for this reason must be stopped on a land with mobs disabled. */
    public static boolean isBlockedSpawnReason(String reasonName) {
        return BLOCKED_SPAWN_REASONS.contains(reasonName);
    }

    private static boolean endsWithAny(String value, String[] suffixes) {
        for (String suffix : suffixes) {
            if (value.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
