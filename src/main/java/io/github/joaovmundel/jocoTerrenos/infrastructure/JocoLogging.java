package io.github.joaovmundel.jocoTerrenos.infrastructure;

import lombok.AllArgsConstructor;
import org.bukkit.Bukkit;

import java.util.logging.Logger;

@AllArgsConstructor
public class JocoLogging {
    private static final Logger logging = Bukkit.getLogger();
    private String className;

    public void info(String message) {
        logging.info("[JocoTerrenos] " + className);
        logging.info("[JocoTerrenos] " + message);
    }

    public void severe(String message) {
        logging.severe("[JocoTerrenos] " + className);
        logging.severe("[JocoTerrenos] " + message);
    }

    public void warning(String message) {
        logging.warning("[JocoTerrenos] " + className);
        logging.warning("[JocoTerrenos] " + message);
    }

}
