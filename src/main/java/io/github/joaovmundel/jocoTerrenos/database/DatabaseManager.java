package io.github.joaovmundel.jocoTerrenos.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class DatabaseManager {
    private final Plugin plugin;
    private final FileConfiguration config;
    private HikariDataSource dataSource;
    @Getter
    private DatabaseType databaseType;

    public enum DatabaseType {
        SQLITE, MYSQL, POSTGRESQL
    }

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    /**
     * Inicializa a conexão com o banco de dados
     */
    public void initialize() {
        String typeString = config.getString("database.type", "SQLITE").toUpperCase();

        try {
            this.databaseType = DatabaseType.valueOf(typeString);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Tipo de banco de dados inválido: " + typeString + ". Usando SQLite como padrão.");
            this.databaseType = DatabaseType.SQLITE;
        }

        HikariConfig hikariConfig = new HikariConfig();

        switch (databaseType) {
            case SQLITE:
                setupSQLite(hikariConfig);
                break;
            case MYSQL:
                setupMySQL(hikariConfig);
                break;
            case POSTGRESQL:
                setupPostgreSQL(hikariConfig);
                break;
        }

        // Configurações do pool
        hikariConfig.setMaximumPoolSize(config.getInt("database.pool.maximum-pool-size", 10));
        hikariConfig.setMinimumIdle(config.getInt("database.pool.minimum-idle", 2));
        hikariConfig.setMaxLifetime(config.getLong("database.pool.maximum-lifetime", 1800000));
        hikariConfig.setConnectionTimeout(config.getLong("database.pool.connection-timeout", 5000));
        hikariConfig.setIdleTimeout(config.getLong("database.pool.idle-timeout", 600000));

        try {
            this.dataSource = new HikariDataSource(hikariConfig);
            plugin.getLogger().info("Conexão com o banco de dados " + databaseType + " estabelecida com sucesso!");

            // Cria as tabelas necessárias
            createTables();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao conectar com o banco de dados!", e);
        }
    }

    /**
     * Configura o SQLite
     */
    private void setupSQLite(HikariConfig config) {
        String filename = this.config.getString("database.sqlite.filename", "terrenos.db");
        File databaseFile = new File(plugin.getDataFolder(), filename);

        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setPoolName("JocoTerrenos-SQLite");

        // Configurações específicas do SQLite
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
    }

    /**
     * Configura o MySQL
     */
    private void setupMySQL(HikariConfig config) {
        String host = this.config.getString("database.mysql.host", "localhost");
        int port = this.config.getInt("database.mysql.port", 3306);
        String database = this.config.getString("database.mysql.database", "jocoterrenos");
        String username = this.config.getString("database.mysql.username", "root");
        String password = this.config.getString("database.mysql.password", "senha");

        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setPoolName("JocoTerrenos-MySQL");

        // Propriedades adicionais do MySQL
        config.addDataSourceProperty("useSSL", this.config.getBoolean("database.mysql.properties.useSSL", false));
        config.addDataSourceProperty("autoReconnect", this.config.getBoolean("database.mysql.properties.autoReconnect", true));
        config.addDataSourceProperty("cachePrepStmts", this.config.getBoolean("database.mysql.properties.cachePrepStmts", true));
        config.addDataSourceProperty("prepStmtCacheSize", this.config.getInt("database.mysql.properties.prepStmtCacheSize", 250));
        config.addDataSourceProperty("prepStmtCacheSqlLimit", this.config.getInt("database.mysql.properties.prepStmtCacheSqlLimit", 2048));
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
    }

    /**
     * Configura o PostgreSQL
     */
    private void setupPostgreSQL(HikariConfig config) {
        String host = this.config.getString("database.postgresql.host", "localhost");
        int port = this.config.getInt("database.postgresql.port", 5432);
        String database = this.config.getString("database.postgresql.database", "jocoterrenos");
        String username = this.config.getString("database.postgresql.username", "postgres");
        String password = this.config.getString("database.postgresql.password", "senha");
        String schema = this.config.getString("database.postgresql.schema", "public");

        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database + "?currentSchema=" + schema);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setPoolName("JocoTerrenos-PostgreSQL");

        // Propriedades adicionais do PostgreSQL
        config.addDataSourceProperty("ssl", this.config.getBoolean("database.postgresql.properties.ssl", false));
        config.addDataSourceProperty("autoReconnect", this.config.getBoolean("database.postgresql.properties.autoReconnect", true));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    }

    /**
     * Cria as tabelas necessárias no banco de dados
     */
    private void createTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            // Tabela de Terrenos
            String createTerrenosTable;
            if (databaseType == DatabaseType.SQLITE) {
                createTerrenosTable = """
                            CREATE TABLE IF NOT EXISTS terrenos (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                dono_uuid VARCHAR(36) NOT NULL,
                                name TEXT,
                                db_name_key TEXT,
                                location TEXT NOT NULL,
                                size INTEGER NOT NULL,
                                pvp BOOLEAN DEFAULT FALSE,
                                mobs BOOLEAN DEFAULT FALSE,
                                public_access BOOLEAN DEFAULT FALSE,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                            );
                        """;
                stmt.execute(createTerrenosTable);
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_name_key ON terrenos(db_name_key)");
            } else if (databaseType == DatabaseType.MYSQL) {
                createTerrenosTable = """
                            CREATE TABLE IF NOT EXISTS terrenos (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                dono_uuid VARCHAR(36) NOT NULL,
                                name VARCHAR(255),
                                db_name_key VARCHAR(300),
                                location TEXT NOT NULL,
                                size INT NOT NULL,
                                pvp BOOLEAN DEFAULT FALSE,
                                mobs BOOLEAN DEFAULT FALSE,
                                public_access BOOLEAN DEFAULT FALSE,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                UNIQUE KEY uk_name_key (db_name_key),
                                INDEX idx_dono_uuid (dono_uuid)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                        """;
                stmt.execute(createTerrenosTable);
            } else { // PostgreSQL
                createTerrenosTable = """
                            CREATE TABLE IF NOT EXISTS terrenos (
                                id BIGSERIAL PRIMARY KEY,
                                dono_uuid VARCHAR(36) NOT NULL,
                                name VARCHAR(255),
                                db_name_key VARCHAR(300),
                                location TEXT NOT NULL,
                                size INTEGER NOT NULL,
                                pvp BOOLEAN DEFAULT FALSE,
                                mobs BOOLEAN DEFAULT FALSE,
                                public_access BOOLEAN DEFAULT FALSE,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                            );
                        """;
                stmt.execute(createTerrenosTable);
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_name_key ON terrenos(db_name_key)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_dono_uuid ON terrenos(dono_uuid)");
            }

            // Tabela de Membros do Terreno
            String createMembersTable;
            if (databaseType == DatabaseType.SQLITE) {
                createMembersTable = """
                            CREATE TABLE IF NOT EXISTS terreno_members (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                terreno_id INTEGER NOT NULL,
                                member_uuid VARCHAR(36) NOT NULL,
                                member_role VARCHAR(20) NOT NULL,
                                added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (terreno_id) REFERENCES terrenos(id) ON DELETE CASCADE,
                                UNIQUE(terreno_id, member_uuid)
                            )
                        """;
            } else if (databaseType == DatabaseType.MYSQL) {
                createMembersTable = """
                            CREATE TABLE IF NOT EXISTS terreno_members (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                terreno_id BIGINT NOT NULL,
                                member_uuid VARCHAR(36) NOT NULL,
                                member_role VARCHAR(20) NOT NULL,
                                added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (terreno_id) REFERENCES terrenos(id) ON DELETE CASCADE,
                                UNIQUE KEY unique_member (terreno_id, member_uuid),
                                INDEX idx_member_uuid (member_uuid)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                        """;
            } else { // PostgreSQL
                createMembersTable = """
                            CREATE TABLE IF NOT EXISTS terreno_members (
                                id BIGSERIAL PRIMARY KEY,
                                terreno_id BIGINT NOT NULL,
                                member_uuid VARCHAR(36) NOT NULL,
                                member_role VARCHAR(20) NOT NULL,
                                added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (terreno_id) REFERENCES terrenos(id) ON DELETE CASCADE,
                                UNIQUE(terreno_id, member_uuid)
                            );
                            CREATE INDEX IF NOT EXISTS idx_member_uuid ON terreno_members(member_uuid);
                        """;
            }

            stmt.execute(createMembersTable);

            plugin.getLogger().info("Tabelas do banco de dados criadas/verificadas com sucesso!");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao criar tabelas do banco de dados!", e);
        }
    }

    /**
     * Obtém uma conexão com o banco de dados
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource não foi inicializado!");
        }
        return dataSource.getConnection();
    }

    /**
     * Fecha a conexão com o banco de dados
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Conexão com o banco de dados fechada com sucesso!");
        }
    }

    /**
     * Verifica se a conexão está ativa
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
}

