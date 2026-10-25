package io.github.joaovmundel.jocoTerrenos.database;

import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.repositories.TerrenoRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Databases created by earlier versions must keep working: opening one adds the columns that came later and
 * keeps every row.
 */
public class SchemaTest {

    /** The terrenos table exactly as the first release of the port created it (no sale_price_cents). */
    private static final String FIRST_RELEASE_TERRENOS = "CREATE TABLE terrenos ("
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
            + "updated_at BIGINT NOT NULL)";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private SqliteConnectionProvider provider;

    @Before
    public void setUp() throws Exception {
        Logger logger = Logger.getLogger("SchemaTest");
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
        provider = new SqliteConnectionProvider(folder.newFile("old.db"), logger);
    }

    @After
    public void tearDown() {
        provider.close();
    }

    private void execute(String sql) throws SQLException {
        Statement statement = provider.get().createStatement();
        try {
            statement.execute(sql);
        } finally {
            statement.close();
        }
    }

    private boolean hasColumn(String column) throws SQLException {
        ResultSet columns = provider.get().getMetaData().getColumns(null, null, "terrenos", column);
        try {
            return columns.next();
        } finally {
            columns.close();
        }
    }

    @Test
    public void aFreshDatabaseHasTheSaleColumn() throws Exception {
        Schema.create(provider.get(), Schema.Dialect.SQLITE);

        assertTrue(hasColumn("sale_price_cents"));
    }

    @Test
    public void anOldDatabaseGainsTheSaleColumnAndKeepsItsRows() throws Exception {
        execute(FIRST_RELEASE_TERRENOS);
        execute("INSERT INTO terrenos (dono_nome, name, db_name_key, location, size, pvp, mobs, public_access, created_at, updated_at) "
                + "VALUES ('joao', 'Casa', 'joao+casa', 'world:100.50:64.00:200.50', 10, 0, 1, 0, 1, 1)");

        Schema.create(provider.get(), Schema.Dialect.SQLITE);

        assertTrue(hasColumn("sale_price_cents"));
        Logger quiet = Logger.getLogger("SchemaTest");
        Terreno kept = new TerrenoRepository(provider, quiet).findByNameKey(TerrenoRepository.nameKey("joao", "casa"));
        assertNotNull(kept);
        assertEquals("Casa", kept.getName());
        assertNull("an old row is not for sale", kept.getSalePrice());
    }

    @Test
    public void runningItTwiceChangesNothing() throws Exception {
        Schema.create(provider.get(), Schema.Dialect.SQLITE);
        Schema.create(provider.get(), Schema.Dialect.SQLITE);

        assertTrue(hasColumn("sale_price_cents"));
    }

    @Test
    public void membersTableIsCreatedToo() throws Exception {
        Schema.create(provider.get(), Schema.Dialect.SQLITE);

        Connection connection = provider.get();
        ResultSet tables = connection.getMetaData().getTables(null, null, "terreno_members", null);
        try {
            assertTrue(tables.next());
        } finally {
            tables.close();
        }
    }
}
