package io.github.joaovmundel.jocoTerrenos.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * MySQL through the Connector/J bundled with CraftBukkit/Spigot. Timeouts are set so that a database that
 * stops answering blocks the server for a bounded time only.
 */
public final class MySqlConnectionProvider extends AbstractConnectionProvider {

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int SOCKET_TIMEOUT_MS = 60000;

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    public MySqlConnectionProvider(String host, int port, String database, String username, String password, Logger logger) {
        super(logger);
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    @Override
    protected Connection open() throws SQLException {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("Driver MySQL (com.mysql.jdbc.Driver) não encontrado. O servidor deveria "
                    + "trazê-lo; use o SQLite ou instale o driver.", exception);
        }

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=UTF-8&connectTimeout=" + CONNECT_TIMEOUT_MS
                + "&socketTimeout=" + SOCKET_TIMEOUT_MS;
        Connection connection = DriverManager.getConnection(url, username, password);
        logger.fine("Conexão com o MySQL estabelecida (" + host + ":" + port + "/" + database + ").");
        return connection;
    }

    /** The server may have closed an idle connection (wait_timeout), which only a round trip reveals. */
    @Override
    protected boolean isUsable(Connection candidate) {
        try {
            if (candidate.isClosed()) {
                return false;
            }
            Statement ping = candidate.createStatement();
            try {
                ping.execute("SELECT 1");
            } finally {
                ping.close();
            }
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }
}
