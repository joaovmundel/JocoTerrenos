package io.github.joaovmundel.jocoTerrenos.repositories;

import io.github.joaovmundel.jocoTerrenos.database.Schema;
import io.github.joaovmundel.jocoTerrenos.database.SqliteConnectionProvider;
import io.github.joaovmundel.jocoTerrenos.exceptions.StorageException;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.models.TerrenoMember;
import io.github.joaovmundel.jocoTerrenos.models.TerrenoRole;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Runs the repository against a real SQLite file through the provider the plugin uses, with the same
 * driver (sqlite-jdbc 3.7.2) that CraftBukkit/Spigot 1.5.2 ships.
 */
public class TerrenoRepositoryTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private SqliteConnectionProvider provider;
    private TerrenoRepository repository;

    @Before
    public void setUp() throws Exception {
        // Some tests provoke SQL errors on purpose (duplicate keys); the stack traces are expected noise.
        Logger logger = Logger.getLogger("TerrenoRepositoryTest");
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
        provider = new SqliteConnectionProvider(folder.newFile("terrenos.db"), logger);
        Schema.create(provider.get(), Schema.Dialect.SQLITE);
        repository = new TerrenoRepository(provider, logger);
    }

    @After
    public void tearDown() {
        provider.close();
    }

    private static Terreno terreno(String dono, String name) {
        Terreno terreno = new Terreno();
        terreno.setDonoNome(dono);
        terreno.setName(name);
        terreno.setLocation("world:100.50:64.00:200.50");
        terreno.setSize(10);
        terreno.setPvp(false);
        terreno.setMobs(true);
        terreno.setPublicAccess(false);
        return terreno;
    }

    @Test
    public void createAssignsAnIdAndFindByIdReadsItBack() {
        Terreno created = repository.create(terreno("joao", "Casa"));

        assertNotNull(created);
        assertNotNull(created.getId());
        Terreno found = repository.findById(created.getId());
        assertNotNull(found);
        assertEquals("joao", found.getDonoNome());
        assertEquals("Casa", found.getName());
        assertEquals("world:100.50:64.00:200.50", found.getLocation());
        assertEquals(Integer.valueOf(10), found.getSize());
        assertFalse(found.getPvp());
        assertTrue(found.getMobs());
        assertFalse(found.getPublicAccess());
    }

    @Test
    public void idsAreDistinct() {
        Terreno first = repository.create(terreno("joao", "a"));
        Terreno second = repository.create(terreno("joao", "b"));

        assertFalse(first.getId().equals(second.getId()));
    }

    @Test
    public void findByIdOfUnknownLandIsNull() {
        assertNull(repository.findById(999L));
    }

    @Test
    public void findByNameKeyIgnoresCase() {
        repository.create(terreno("Joao", "Casa Grande"));

        Terreno found = repository.findByNameKey(TerrenoRepository.nameKey("JOAO", "casa grande"));

        assertNotNull(found);
        assertEquals("Casa Grande", found.getName());
    }

    @Test
    public void sameNameForTheSameOwnerIsRejectedCaseInsensitively() {
        assertNotNull(repository.create(terreno("joao", "Casa")));

        assertNull(repository.create(terreno("JOAO", "CASA")));
        assertTrue(repository.existsByOwnerAndName("joao", "casa"));
    }

    @Test
    public void sameNameForAnotherOwnerIsFine() {
        assertNotNull(repository.create(terreno("joao", "Casa")));

        assertNotNull(repository.create(terreno("maria", "Casa")));
        assertFalse(repository.existsByOwnerAndName("pedro", "Casa"));
    }

    @Test
    public void findByDonoReturnsOnlyThatOwnersLands() {
        repository.create(terreno("joao", "a"));
        repository.create(terreno("joao", "b"));
        repository.create(terreno("maria", "c"));

        assertEquals(2, repository.findByDono("joao").size());
        assertEquals(1, repository.findByDono("maria").size());
        assertTrue(repository.findByDono("ninguem").isEmpty());
    }

    @Test
    public void updatePersistsFlagsAndRenames() {
        Terreno created = repository.create(terreno("joao", "Casa"));
        created.setPvp(true);
        created.setMobs(false);
        created.setPublicAccess(true);
        created.setName("Mansao");

        assertTrue(repository.update(created));

        Terreno found = repository.findById(created.getId());
        assertTrue(found.getPvp());
        assertFalse(found.getMobs());
        assertTrue(found.getPublicAccess());
        assertEquals("Mansao", found.getName());
        assertNull(repository.findByNameKey(TerrenoRepository.nameKey("joao", "Casa")));
        assertNotNull(repository.findByNameKey(TerrenoRepository.nameKey("joao", "Mansao")));
    }

    @Test
    public void updateOfUnknownLandReportsFailure() {
        Terreno ghost = terreno("joao", "fantasma");
        ghost.setId(999L);

        assertFalse(repository.update(ghost));
    }

    @Test
    public void deleteRemovesTheLand() {
        Terreno created = repository.create(terreno("joao", "Casa"));

        assertTrue(repository.delete(created.getId()));

        assertNull(repository.findById(created.getId()));
        assertFalse(repository.delete(created.getId()));
    }

    @Test
    public void membersCanBeAddedChangedAndRemoved() {
        Terreno created = repository.create(terreno("joao", "Casa"));
        long id = created.getId();

        assertTrue(repository.addMember(id, "maria", TerrenoRole.MEMBER));
        assertFalse("a member can only be added once", repository.addMember(id, "maria", TerrenoRole.ADMIN));
        assertTrue(repository.updateMemberRole(id, "maria", TerrenoRole.ADMIN));

        List<TerrenoMember> members = repository.findMembersByTerrenoId(id);
        assertEquals(1, members.size());
        assertEquals("maria", members.get(0).getMemberNome());
        assertEquals(TerrenoRole.ADMIN, members.get(0).getMemberRole());
        assertEquals(1, repository.findById(id).getMembers().size());

        assertTrue(repository.removeMember(id, "maria"));
        assertTrue(repository.findMembersByTerrenoId(id).isEmpty());
        assertFalse(repository.removeMember(id, "maria"));
    }

    @Test
    public void deletingALandTakesItsMembersWithIt() throws Exception {
        Terreno created = repository.create(terreno("joao", "Casa"));
        repository.addMember(created.getId(), "maria", TerrenoRole.MEMBER);

        repository.delete(created.getId());

        assertTrue(repository.findMembersByTerrenoId(created.getId()).isEmpty());
        assertEquals(0, countRows("terreno_members"));
    }

    @Test
    public void loadAllReturnsEveryLandWithItsMembers() throws Exception {
        Terreno casa = repository.create(terreno("joao", "Casa"));
        Terreno sitio = repository.create(terreno("maria", "Sitio"));
        repository.addMember(casa.getId(), "pedro", TerrenoRole.MEMBER);
        repository.addMember(casa.getId(), "ana", TerrenoRole.ADMIN);

        List<Terreno> all = repository.loadAll();

        assertEquals(2, all.size());
        Terreno loadedCasa = all.get(0).getId().equals(casa.getId()) ? all.get(0) : all.get(1);
        Terreno loadedSitio = all.get(0).getId().equals(sitio.getId()) ? all.get(0) : all.get(1);
        assertEquals(2, loadedCasa.getMembers().size());
        assertTrue(loadedSitio.getMembers().isEmpty());
    }

    @Test
    public void loadAllOfAnEmptyDatabaseIsEmptyNotAnError() throws Exception {
        assertTrue(repository.loadAll().isEmpty());
    }

    @Test
    public void loadAllReportsAnUnknownRoleInsteadOfLoadingHalf() throws Exception {
        Terreno created = repository.create(terreno("joao", "Casa"));
        execute("INSERT INTO terreno_members (terreno_id, member_nome, member_role, added_at) VALUES ("
                + created.getId() + ", 'maria', 'ROLE_FROM_THE_FUTURE', 0)");

        try {
            repository.loadAll();
            fail("an unreadable database must not look like a smaller one");
        } catch (StorageException expected) {
            assertNotNull(expected.getCause());
        }
    }

    @Test
    public void loadAllReportsAMissingTable() throws Exception {
        execute("DROP TABLE terreno_members");
        execute("DROP TABLE terrenos");

        try {
            repository.loadAll();
            fail("a missing table must be reported");
        } catch (StorageException expected) {
            assertTrue(expected.getCause() instanceof SQLException);
        }
    }

    @Test
    public void flyingPlayersAreRecordedAndForgotten() {
        assertTrue(repository.loadFlyingPlayers().isEmpty());

        assertTrue(repository.markFlying("joao"));
        assertTrue("marking twice is harmless", repository.markFlying("joao"));
        assertTrue(repository.markFlying("maria"));

        assertEquals(2, repository.loadFlyingPlayers().size());
        assertTrue(repository.loadFlyingPlayers().contains("joao"));

        assertTrue(repository.unmarkFlying("joao"));
        assertFalse("nothing left to remove", repository.unmarkFlying("joao"));
        assertEquals(1, repository.loadFlyingPlayers().size());
    }

    @Test
    public void providerReconnectsAfterTheConnectionWasClosed() throws Exception {
        repository.create(terreno("joao", "Casa"));

        provider.get().close();

        assertEquals(1, repository.findByDono("joao").size());
    }

    private void execute(String sql) throws SQLException {
        Connection connection = provider.get();
        Statement statement = connection.createStatement();
        try {
            statement.execute(sql);
        } finally {
            statement.close();
        }
    }

    private int countRows(String table) throws SQLException {
        Connection connection = provider.get();
        Statement statement = connection.createStatement();
        try {
            java.sql.ResultSet rs = statement.executeQuery("SELECT COUNT(1) FROM " + table);
            try {
                rs.next();
                return rs.getInt(1);
            } finally {
                rs.close();
            }
        } finally {
            statement.close();
        }
    }
}
