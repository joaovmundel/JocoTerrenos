package io.github.joaovmundel.jocoTerrenos.infrastructure;

import org.bukkit.Bukkit;

import java.util.logging.Logger;

public class JocoLogging {
    private static final Logger logging = Bukkit.getLogger();
    private final String className;

    public JocoLogging(String className) {
        this.className = className;
    }

    public void info(String message) {
        logging.info("[JocoTerrenos] " + className);
        logging.info("[JocoTerrenos] " + message);
    }

    public void warning(String message) {
        logging.warning("[JocoTerrenos] " + className);
        logging.warning("[JocoTerrenos] " + message);
    }

}
