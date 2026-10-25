package io.github.joaovmundel.jocoTerrenos.protection;

/**
 * Who may fly where with {@code /voar}: a player with the flight permission, in a world where lands can be bought,
 * standing inside a land they own or are a member of. Nobody else, whatever permissions they hold: not on
 * unclaimed ground, not on a land that belongs to someone else, not even a public one.
 */
public final class FlightRules {

    public enum Verdict {
        ALLOWED,
        NO_PERMISSION,
        WORLD_NOT_ALLOWED,
        NOT_IN_OWN_LAND
    }

    private FlightRules() {
    }

    /**
     * @param claim the land at the player's position, or {@code null} for unclaimed ground
     * @return why the player cannot fly there, or {@link Verdict#ALLOWED}; the first failing rule wins
     */
    public static Verdict check(boolean hasPermission, boolean worldAllowed, Claim claim, String playerKey) {
        if (!hasPermission) {
            return Verdict.NO_PERMISSION;
        }
        if (!worldAllowed) {
            return Verdict.WORLD_NOT_ALLOWED;
        }
        if (claim == null || !claim.allowsBuild(playerKey)) {
            return Verdict.NOT_IN_OWN_LAND;
        }
        return Verdict.ALLOWED;
    }
}
