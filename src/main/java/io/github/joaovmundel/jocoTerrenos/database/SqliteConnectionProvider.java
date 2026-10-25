package io.github.joaovmundel.jocoTerrenos.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/** SQLite through the driver bundled with CraftBukkit/Spigot (sqlite-jdbc 3.7.2 on 1.5.2). */
public final class SqliteConnectionProvider extends AbstractConnectionProvider {

    private final File file;

    public SqliteConnectionProvider(File file, Logger logger) {
        super(logger);
        this.file = file.getAbsoluteFile();
    }

    @Override
    protected Connection open() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("Driver SQLite (org.sqlite.JDBC) nao encontrado. O servidor deveria traze-lo; "
                    + "use o MySQL ou instale o driver.", exception);
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Nao foi possivel criar a pasta " + parent.getPath());
        }

        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getPath().replace('\\', '/'));
        enableForeignKeys(connection);
        debug.debug("Banco de dados SQLite aberto: " + file.getName());
        return connection;
    }

    /** SQLite ignores FOREIGN KEY ... ON DELETE CASCADE unless this is switched on for every connection. */
    private void enableForeignKeys(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        try {
            statement.execute("PRAGMA foreign_keys = ON");
        } finally {
            statement.close();
        }
    }

    /** A local file cannot drop the connection; only an explicit close makes it unusable. */
    @Override
    protected boolean isUsable(Connection candidate) {
        try {
            return !candidate.isClosed();
        } catch (SQLException exception) {
            return false;
        }
    }
}
