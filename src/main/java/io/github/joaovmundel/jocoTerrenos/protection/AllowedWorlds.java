package io.github.joaovmundel.jocoTerrenos.protection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The worlds where lands can be bought (and where {@code /voar} works), from {@code lands.allowed-worlds}.
 * World names are compared ignoring case. An empty list means no restriction, so a config written before this
 * setting existed keeps working in every world.
 */
public final class AllowedWorlds {

    private final Set<String> lowercaseNames = new HashSet<String>();
    private final List<String> names = new ArrayList<String>();

    public AllowedWorlds(Collection<String> configured) {
        for (String name : configured) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            String trimmed = name.trim();
            if (lowercaseNames.add(trimmed.toLowerCase(Locale.ENGLISH))) {
                names.add(trimmed);
            }
        }
    }

    /** @return whether only the listed worlds are allowed */
    public boolean isRestricted() {
        return !lowercaseNames.isEmpty();
    }

    public boolean allows(String worldName) {
        return !isRestricted() || lowercaseNames.contains(worldName.toLowerCase(Locale.ENGLISH));
    }

    /** @return the allowed worlds as typed in the config, separated by commas ({@code ""} when unrestricted) */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(name);
        }
        return sb.toString();
    }
}
