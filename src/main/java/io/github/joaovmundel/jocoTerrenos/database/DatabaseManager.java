package io.github.joaovmundel.jocoTerrenos.database;

import io.github.joaovmundel.jocoTerrenos.exceptions.StorageException;
import io.github.joaovmundel.jocoTerrenos.infrastructure.JocoLogging;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Chooses the database from {@code config.yml}, opens it and creates the tables. SQLite and MySQL only: those
 * are the drivers the 1.5.2 server ships with.
 */
public class DatabaseManager {

    private static final String DEFAULT_SQLITE_FILE = "terrenos.db";
    private static final int DEFAULT_MYSQL_PORT = 3306;

    private final Plugin plugin;
    private final FileConfiguration config;
    private final Logger logger;
    private final JocoLogging debug = new JocoLogging(DatabaseManager.class.getName());
    private ConnectionProvider provider;

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.logger = plugin.getLogger();
    }

    /**
     * Opens the database and creates the tables.
     *
     * @throws StorageException if the database cannot be reached or prepared
     */
    public void initialize() throws StorageException {
        Schema.Dialect dialect = readDialect();
        ConnectionProvider candidate = dialect == Schema.Dialect.MYSQL ? mysqlProvider() : sqliteProvider();
        try {
            Schema.create(candidate.get(), dialect);
        } catch (SQLException e) {
            candidate.close();
            throw new StorageException("Nao foi possivel abrir o banco de dados " + dialect, e);
        }
        this.provider = candidate;
        debug.debug("Banco de dados " + dialect + " pronto.");
    }

    private Schema.Dialect readDialect() {
        String configured = config.getString("database.type", "SQLITE").toUpperCase(Locale.ENGLISH);
        try {
            return Schema.Dialect.valueOf(configured);
        } catch (IllegalArgumentException e) {
            logger.warning("Tipo de banco de dados invalido em database.type: " + configured
                    + ". Use SQLITE ou MYSQL. Usando SQLite como padrao.");
            return Schema.Dialect.SQLITE;
        }
    }

    private ConnectionProvider sqliteProvider() {
        String filename = config.getString("database.sqlite.filename", DEFAULT_SQLITE_FILE);
        return new SqliteConnectionProvider(new File(plugin.getDataFolder(), filename), logger);
    }

    private ConnectionProvider mysqlProvider() {
        return new MySqlConnectionProvider(
                config.getString("database.mysql.host", "localhost"),
                config.getInt("database.mysql.port", DEFAULT_MYSQL_PORT),
                config.getString("database.mysql.database", "jocoterrenos"),
                config.getString("database.mysql.username", "root"),
                config.getString("database.mysql.password", ""),
                logger);
    }

    /** @return the provider the repositories read from; only valid after {@link #initialize()} succeeded */
    public ConnectionProvider getConnectionProvider() {
        return provider;
    }

    public boolean isConnected() {
        if (provider == null) {
            return false;
        }
        try {
            Connection connection = provider.get();
            return !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public void close() {
        if (provider != null) {
            provider.close();
            provider = null;
            debug.debug("Conexao com o banco de dados fechada com sucesso!");
        }
    }
}
