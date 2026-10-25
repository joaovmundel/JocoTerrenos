package io.github.joaovmundel.jocoTerrenos.infrastructure;

import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.util.logging.Logger;

/**
 * Console logging of the plugin: one line per message, tagged with the plugin name. {@link #debug} lines only
 * appear when {@code debug: true} is set in {@code config.yml}.
 */
public class JocoLogging {
    private static final String TAG = "[JocoTerrenos] ";

    private static volatile boolean debugEnabled;

    private final String className;

    public JocoLogging(String className) {
        this.className = className;
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public void info(String message) {
        logger().info(TAG + message);
    }

    public void warning(String message) {
        logger().warning(TAG + message);
    }

    /** Shown only in debug mode, with the class that logged it. */
    public void debug(String message) {
        if (debugEnabled) {
            logger().info(TAG + "[debug] " + simpleName() + ": " + message);
        }
    }

    private String simpleName() {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    /** Looked up on every call so the class also works where there is no server (unit tests). */
    private static Logger logger() {
        Server server = Bukkit.getServer();
        return server != null ? server.getLogger() : Logger.getLogger("JocoTerrenos");
    }
}
