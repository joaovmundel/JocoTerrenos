package io.github.joaovmundel.jocoTerrenos.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Tables of the plugin. Plain JDBC with no Bukkit types, so tests can create the same schema in memory.
 * <p>
 * Kept to SQL the drivers bundled with 1.5.2 understand (SQLite 3.7.2, MySQL 5.1 Connector/J): timestamps are
 * epoch milliseconds set by the code because old MySQL allows only one CURRENT_TIMESTAMP column per table,
 * and the unique name key is short enough for the 767 byte InnoDB index limit.
 */
public final class Schema {

    public enum Dialect {
        SQLITE, MYSQL
    }

    private Schema() {
    }

    public static void create(Connection connection, Dialect dialect) throws SQLException {
        Statement statement = connection.createStatement();
        try {
            if (dialect == Dialect.SQLITE) {
                createSqlite(statement);
            } else {
                createMySql(statement);
            }
        } finally {
            statement.close();
        }
    }

    private static void createSqlite(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS terrenos ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "dono_nome VARCHAR(32) NOT NULL, "
                + "name VARCHAR(32) NOT NULL, "
                + "db_name_key VARCHAR(80) NOT NULL, "
                + "location VARCHAR(255) NOT NULL, "
                + "size INTEGER NOT NULL, "
                + "pvp BOOLEAN NOT NULL DEFAULT 0, "
                + "mobs BOOLEAN NOT NULL DEFAULT 1, "
                + "public_access BOOLEAN NOT NULL DEFAULT 0, "
                + "created_at BIGINT NOT NULL, "
                + "updated_at BIGINT NOT NULL)");
        statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_name_key ON terrenos(db_name_key)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_dono_nome ON terrenos(dono_nome)");
        statement.execute("CREATE TABLE IF NOT EXISTS terreno_members ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "terreno_id INTEGER NOT NULL, "
                + "member_nome VARCHAR(32) NOT NULL, "
                + "member_role VARCHAR(20) NOT NULL, "
                + "added_at BIGINT NOT NULL, "
                + "FOREIGN KEY (terreno_id) REFERENCES terrenos(id) ON DELETE CASCADE, "
                + "UNIQUE (terreno_id, member_nome))");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_member_nome ON terreno_members(member_nome)");
    }

    private static void createMySql(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS terrenos ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "dono_nome VARCHAR(32) NOT NULL, "
                + "name VARCHAR(32) NOT NULL, "
                + "db_name_key VARCHAR(80) NOT NULL, "
                + "location VARCHAR(255) NOT NULL, "
                + "size INT NOT NULL, "
                + "pvp BOOLEAN NOT NULL DEFAULT 0, "
                + "mobs BOOLEAN NOT NULL DEFAULT 1, "
                + "public_access BOOLEAN NOT NULL DEFAULT 0, "
                + "created_at BIGINT NOT NULL, "
                + "updated_at BIGINT NOT NULL, "
                + "UNIQUE KEY uk_name_key (db_name_key), "
                + "INDEX idx_dono_nome (dono_nome)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8");
        statement.execute("CREATE TABLE IF NOT EXISTS terreno_members ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "terreno_id BIGINT NOT NULL, "
                + "member_nome VARCHAR(32) NOT NULL, "
                + "member_role VARCHAR(20) NOT NULL, "
                + "added_at BIGINT NOT NULL, "
                + "FOREIGN KEY (terreno_id) REFERENCES terrenos(id) ON DELETE CASCADE, "
                + "UNIQUE KEY unique_member (terreno_id, member_nome), "
                + "INDEX idx_member_nome (member_nome)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8");
    }
}
