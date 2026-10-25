package io.github.joaovmundel.jocoTerrenos.utils;

import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Minecraft 1.5.2 has no UUIDs: players are identified by name, case-insensitively. The server runs in
 * offline mode, so names are only trustworthy while AuthMe guards the logins.
 */
public final class PlayerKeys {

    private PlayerKeys() {
    }

    public static String of(Player player) {
        return of(player.getName());
    }

    public static String of(String playerName) {
        return playerName.toLowerCase(Locale.ENGLISH);
    }
}
