package io.github.joaovmundel.jocoTerrenos.database;

import java.sql.Connection;
import java.sql.SQLException;

/** Hands out the connection to the database, reconnecting when it was lost. */
public interface ConnectionProvider {

    /**
     * @return a working connection. Owned by the provider: callers must not close it. It is shared, so it
     * must only be used from the main thread.
     */
    Connection get() throws SQLException;

    void close();
}
