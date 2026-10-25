package io.github.joaovmundel.jocoTerrenos.repositories;

import io.github.joaovmundel.jocoTerrenos.database.ConnectionProvider;
import io.github.joaovmundel.jocoTerrenos.economy.SalePrice;
import io.github.joaovmundel.jocoTerrenos.exceptions.StorageException;
import io.github.joaovmundel.jocoTerrenos.infrastructure.JocoLogging;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.models.TerrenoMember;
import io.github.joaovmundel.jocoTerrenos.models.TerrenoRole;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads and writes lands and their members. Every method uses the single shared connection of the
 * {@link ConnectionProvider} (never closed here), so it must be called from the main thread only.
 * <p>
 * Methods that find one land return {@code null} when it does not exist; failures are logged and reported as
 * {@code null}/{@code false}, except {@link #loadAll()} which throws because an unreadable database must not
 * look like an empty one.
 */
public class TerrenoRepository {

    private static final String INSERT_TERRENO = "INSERT INTO terrenos "
            + "(dono_nome, name, db_name_key, location, size, pvp, mobs, public_access, created_at, updated_at, sale_price_cents) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_TERRENO = "UPDATE terrenos SET dono_nome = ?, name = ?, db_name_key = ?, "
            + "location = ?, size = ?, pvp = ?, mobs = ?, public_access = ?, updated_at = ?, sale_price_cents = ? "
            + "WHERE id = ?";

    private final ConnectionProvider connections;
    private final Logger logger;
    private final JocoLogging debug = new JocoLogging(TerrenoRepository.class.getName());

    public TerrenoRepository(ConnectionProvider connections, Logger logger) {
        this.connections = connections;
        this.logger = logger;
    }

    /**
     * Unique key of a land: owner and name, both case-insensitive.
     */
    public static String nameKey(String donoNome, String name) {
        return donoNome.toLowerCase(Locale.ENGLISH) + "+" + name.trim().toLowerCase(Locale.ENGLISH);
    }

    /**
     * Cria um terreno no banco de dados
     *
     * @return the land with its generated id, or {@code null} if it could not be saved
     */
    public Terreno create(Terreno terreno) {
        long now = System.currentTimeMillis();
        try {
            Connection connection = connections.get();
            try (PreparedStatement stmt = connection.prepareStatement(INSERT_TERRENO, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, terreno.getDonoNome());
                stmt.setString(2, terreno.getName());
                stmt.setString(3, nameKey(terreno.getDonoNome(), terreno.getName()));
                stmt.setString(4, terreno.getLocation());
                stmt.setInt(5, terreno.getSize());
                stmt.setBoolean(6, terreno.getPvp());
                stmt.setBoolean(7, terreno.getMobs());
                stmt.setBoolean(8, terreno.getPublicAccess());
                stmt.setLong(9, now);
                stmt.setLong(10, now);
                bindSalePrice(stmt, 11, terreno.getSalePrice());

                if (stmt.executeUpdate() > 0) {
                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            terreno.setId(generatedKeys.getLong(1));
                            debug.debug("Terreno criado com ID: " + terreno.getId());
                            return terreno;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao criar terreno", e);
        }
        return null;
    }

    /**
     * Busca um terreno pelo ID
     *
     * @return the land with its members, or {@code null} if there is none
     */
    public Terreno findById(long id) {
        try {
            Connection connection = connections.get();
            try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM terrenos WHERE id = ?")) {
                stmt.setLong(1, id);
                return readFirstTerreno(stmt);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao buscar terreno por ID: " + id, e);
        }
        return null;
    }

    /**
     * Busca um terreno pela chave de nome (dono + nome)
     *
     * @return the land with its members, or {@code null} if there is none
     */
    public Terreno findByNameKey(String dbNameKey) {
        try {
            Connection connection = connections.get();
            try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM terrenos WHERE db_name_key = ?")) {
                stmt.setString(1, dbNameKey);
                return readFirstTerreno(stmt);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao buscar terreno por nomeKey: " + dbNameKey, e);
        }
        return null;
    }

    private Terreno readFirstTerreno(PreparedStatement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            Terreno terreno = mapResultSetToTerreno(rs);
            terreno.setMembers(findMembersByTerrenoId(terreno.getId()));
            return terreno;
        }
    }

    /**
     * Busca todos os terrenos de um dono
     */
    public List<Terreno> findByDono(String donoNome) {
        List<Terreno> terrenos = new ArrayList<Terreno>();
        try {
            Connection connection = connections.get();
            try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM terrenos WHERE dono_nome = ?")) {
                stmt.setString(1, donoNome);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        terrenos.add(mapResultSetToTerreno(rs));
                    }
                }
            }
            for (Terreno terreno : terrenos) {
                terreno.setMembers(findMembersByTerrenoId(terreno.getId()));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao buscar terrenos do dono: " + donoNome, e);
        }
        return terrenos;
    }

    /**
     * Loads every land with its members using two queries. A failure is reported instead of looking like an
     * empty database, which would leave every land unprotected.
     *
     * @throws StorageException if the database cannot be read or holds an unknown member role
     */
    public List<Terreno> loadAll() throws StorageException {
        Map<Long, Terreno> terrenosById = new LinkedHashMap<Long, Terreno>();

        try {
            Connection connection = connections.get();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM terrenos")) {
                while (rs.next()) {
                    Terreno terreno = mapResultSetToTerreno(rs);
                    terrenosById.put(terreno.getId(), terreno);
                }
            }

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM terreno_members")) {
                while (rs.next()) {
                    Terreno terreno = terrenosById.get(rs.getLong("terreno_id"));
                    if (terreno == null) {
                        continue; // orphan row; the cascade should have removed it
                    }
                    terreno.getMembers().add(mapResultSetToMember(rs));
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            throw new StorageException("Nao foi possivel carregar os terrenos do banco de dados", e);
        }

        return new ArrayList<Terreno>(terrenosById.values());
    }

    /**
     * Atualiza um terreno existente
     */
    public boolean update(Terreno terreno) {
        try {
            if (updateRow(connections.get(), terreno)) {
                debug.debug("Terreno atualizado: " + terreno.getId());
                return true;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao atualizar terreno: " + terreno.getId(), e);
        }
        return false;
    }

    private boolean updateRow(Connection connection, Terreno terreno) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(UPDATE_TERRENO)) {
            stmt.setString(1, terreno.getDonoNome());
            stmt.setString(2, terreno.getName());
            stmt.setString(3, nameKey(terreno.getDonoNome(), terreno.getName()));
            stmt.setString(4, terreno.getLocation());
            stmt.setInt(5, terreno.getSize());
            stmt.setBoolean(6, terreno.getPvp());
            stmt.setBoolean(7, terreno.getMobs());
            stmt.setBoolean(8, terreno.getPublicAccess());
            stmt.setLong(9, System.currentTimeMillis());
            bindSalePrice(stmt, 10, terreno.getSalePrice());
            stmt.setLong(11, terreno.getId());
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Saves a land that changed hands: the new owner, name and flags, and no members, all or nothing. The old
     * members belong to the old owner's trust and must not follow the land to its new owner.
     *
     * @return whether the land was updated; on {@code false} nothing changed in the database
     */
    public boolean transferOwnership(Terreno terreno) {
        Connection connection;
        try {
            connection = connections.get();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao transferir terreno: " + terreno.getId(), e);
            return false;
        }

        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            boolean updated = updateRow(connection, terreno);
            if (updated) {
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM terreno_members WHERE terreno_id = ?")) {
                    stmt.setLong(1, terreno.getId());
                    stmt.executeUpdate();
                }
                connection.commit();
                terreno.getMembers().clear();
                debug.debug("Terreno transferido: " + terreno.getId() + " -> " + terreno.getDonoNome());
                return true;
            }
            connection.rollback();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao transferir terreno: " + terreno.getId(), e);
            rollbackQuietly(connection);
        } finally {
            restoreAutoCommit(connection, previousAutoCommit);
        }
        return false;
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Nao foi possivel desfazer a transacao", e);
        }
    }

    private void restoreAutoCommit(Connection connection, boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Nao foi possivel restaurar o auto-commit da conexao", e);
        }
    }

    /** Stores the price as whole cents, or NULL when the land is not for sale. */
    private static void bindSalePrice(PreparedStatement stmt, int index, BigDecimal price) throws SQLException {
        if (price == null) {
            stmt.setNull(index, Types.BIGINT);
        } else {
            stmt.setLong(index, SalePrice.toCents(price));
        }
    }

    /**
     * Terrenos que estao a venda, do mais barato para o mais caro
     *
     * @param limit how many to return at most
     */
    public List<Terreno> findForSale(int limit) {
        List<Terreno> terrenos = new ArrayList<Terreno>();
        String sql = "SELECT * FROM terrenos WHERE sale_price_cents IS NOT NULL ORDER BY sale_price_cents ASC, id ASC LIMIT ?";
        try {
            Connection connection = connections.get();
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        terrenos.add(mapResultSetToTerreno(rs));
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao listar terrenos a venda", e);
        }
        return terrenos;
    }

    /**
     * Deleta um terreno pelo ID (os membros saem junto, por ON DELETE CASCADE)
     */
    public boolean delete(long id) {
        try {
            Connection connection = connections.get();
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM terrenos WHERE id = ?")) {
                stmt.setLong(1, id);
                if (stmt.executeUpdate() > 0) {
                    debug.debug("Terreno deletado: " + id);
                    return true;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao deletar terreno: " + id, e);
        }
        return false;
    }

    /**
     * Adiciona um membro ao terreno
     */
    public boolean addMember(long terrenoId, String memberNome, TerrenoRole role) {
        String sql = "INSERT INTO terreno_members (terreno_id, member_nome, member_role, added_at) VALUES (?, ?, ?, ?)";
        try {
            Connection connection = connections.get();
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setLong(1, terrenoId);
                stmt.setString(2, memberNome);
                stmt.setString(3, role.name());
                stmt.setLong(4, System.currentTimeMillis());
                if (stmt.executeUpdate() > 0) {
                    debug.debug("Membro adicionado ao terreno " + terrenoId + ": " + memberNome);
                    return true;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao adicionar membro ao terreno: " + terrenoId, e);
        }
        return false;
    }

    /**
     * Remove um membro do terreno
     */
    public boolean removeMember(long terrenoId, String memberNome) {
        try {
            Connection connection = connections.get();
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM terreno_members WHERE terreno_id = ? AND member_nome = ?")) {
                stmt.setLong(1, terrenoId);
                stmt.setString(2, memberNome);
                if (stmt.executeUpdate() > 0) {
                    debug.debug("Membro removido do terreno " + terrenoId + ": " + memberNome);
                    return true;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao remover membro do terreno: " + terrenoId, e);
        }
        return false;
    }

    /**
     * Atualiza o papel de um membro no terreno
     */
    public boolean updateMemberRole(long terrenoId, String memberNome, TerrenoRole newRole) {
        try {
            Connection connection = connections.get();
            try (PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE terreno_members SET member_role = ? WHERE terreno_id = ? AND member_nome = ?")) {
                stmt.setString(1, newRole.name());
                stmt.setLong(2, terrenoId);
                stmt.setString(3, memberNome);
                if (stmt.executeUpdate() > 0) {
                    debug.debug("Papel do membro atualizado no terreno " + terrenoId + ": " + memberNome + " -> " + newRole);
                    return true;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao atualizar papel do membro: " + terrenoId, e);
        }
        return false;
    }

    /**
     * Busca todos os membros de um terreno
     */
    public List<TerrenoMember> findMembersByTerrenoId(long terrenoId) {
        List<TerrenoMember> members = new ArrayList<TerrenoMember>();
        try {
            Connection connection = connections.get();
            try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM terreno_members WHERE terreno_id = ?")) {
                stmt.setLong(1, terrenoId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        members.add(mapResultSetToMember(rs));
                    }
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            logger.log(Level.SEVERE, "Erro ao buscar membros do terreno: " + terrenoId, e);
        }
        return members;
    }

    /**
     * Verifica se o dono já tem um terreno com esse nome (ignorando maiúsculas/minúsculas)
     */
    public boolean existsByOwnerAndName(String donoNome, String nome) {
        try {
            Connection connection = connections.get();
            try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(1) FROM terrenos WHERE db_name_key = ?")) {
                stmt.setString(1, nameKey(donoNome, nome));
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao verificar existencia de nome: " + nome + " para dono: " + donoNome, e);
        }
        return false;
    }

    /**
     * Registers that the player turned {@code /voar} on. The 1.5.2 server saves the "may fly" flag in the player
     * file, so after a crash the flag would outlive the session; this record lets the next login undo it.
     *
     * @param playerNome lowercase player name
     */
    public boolean markFlying(String playerNome) {
        try {
            Connection connection = connections.get();
            if (isFlyingMarked(connection, playerNome)) {
                return true;
            }
            try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO flying_players (player_nome) VALUES (?)")) {
                stmt.setString(1, playerNome);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao registrar voo ativo de " + playerNome, e);
            return false;
        }
    }

    private boolean isFlyingMarked(Connection connection, String playerNome) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(1) FROM flying_players WHERE player_nome = ?")) {
            stmt.setString(1, playerNome);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public boolean unmarkFlying(String playerNome) {
        try {
            Connection connection = connections.get();
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM flying_players WHERE player_nome = ?")) {
                stmt.setString(1, playerNome);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao limpar voo ativo de " + playerNome, e);
            return false;
        }
    }

    /** @return the players recorded as flying, lowercase; empty if the table cannot be read (logged) */
    public Set<String> loadFlyingPlayers() {
        Set<String> names = new HashSet<String>();
        try {
            Connection connection = connections.get();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT player_nome FROM flying_players")) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao ler os voos ativos", e);
        }
        return names;
    }

    private Terreno mapResultSetToTerreno(ResultSet rs) throws SQLException {
        Terreno terreno = new Terreno();
        terreno.setId(rs.getLong("id"));
        terreno.setDonoNome(rs.getString("dono_nome"));
        terreno.setName(rs.getString("name"));
        terreno.setLocation(rs.getString("location"));
        terreno.setSize(rs.getInt("size"));
        terreno.setPvp(rs.getBoolean("pvp"));
        terreno.setMobs(rs.getBoolean("mobs"));
        terreno.setPublicAccess(rs.getBoolean("public_access"));
        long salePriceCents = rs.getLong("sale_price_cents");
        terreno.setSalePrice(rs.wasNull() ? null : SalePrice.fromCents(salePriceCents));
        return terreno;
    }

    private TerrenoMember mapResultSetToMember(ResultSet rs) throws SQLException {
        TerrenoMember member = new TerrenoMember();
        member.setTerrenoId(rs.getLong("terreno_id"));
        member.setMemberNome(rs.getString("member_nome"));
        member.setMemberRole(TerrenoRole.valueOf(rs.getString("member_role")));
        return member;
    }
}
