package io.github.joaovmundel.jocoTerrenos.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps one connection and replaces it when it stops working. One connection is enough because only the main
 * thread uses it, and it avoids a pool library (HikariCP needs Java 11 in its latest line, the server runs Java 8).
 */
abstract class AbstractConnectionProvider implements ConnectionProvider {

    protected final Logger logger;
    private Connection connection;

    protected AbstractConnectionProvider(Logger logger) {
        this.logger = logger;
    }

    @Override
    public synchronized Connection get() throws SQLException {
        if (connection != null) {
            if (isUsable(connection)) {
                return connection;
            }
            logger.warning("A conexão com o banco de dados foi perdida; reconectando...");
            closeQuietly();
        }
        connection = open();
        return connection;
    }

    @Override
    public synchronized void close() {
        closeQuietly();
    }

    /** Opens a brand new connection, or throws an {@link SQLException} that says what to fix. */
    protected abstract Connection open() throws SQLException;

    /** Whether a connection handed out earlier can still be used. */
    protected abstract boolean isUsable(Connection candidate);

    private void closeQuietly() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            logger.log(Level.FINE, "Erro ao fechar a conexão (ignorado)", exception);
        } finally {
            connection = null;
        }
    }
}
