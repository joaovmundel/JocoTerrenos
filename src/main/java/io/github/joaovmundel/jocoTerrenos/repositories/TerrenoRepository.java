package io.github.joaovmundel.jocoTerrenos.repositories;

import io.github.joaovmundel.jocoTerrenos.database.ConnectionProvider;
import io.github.joaovmundel.jocoTerrenos.exceptions.StorageException;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.models.TerrenoMember;
import io.github.joaovmundel.jocoTerrenos.models.TerrenoRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            + "(dono_nome, name, db_name_key, location, size, pvp, mobs, public_access, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final ConnectionProvider connections;
    private final Logger logger;

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

                if (stmt.executeUpdate() > 0) {
                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            terreno.setId(generatedKeys.getLong(1));
                            logger.info("Terreno criado com ID: " + terreno.getId());
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
            throw new StorageException("Não foi possível carregar os terrenos do banco de dados", e);
        }

        return new ArrayList<Terreno>(terrenosById.values());
    }

    /**
     * Atualiza um terreno existente
     */
    public boolean update(Terreno terreno) {
        String sql = "UPDATE terrenos SET dono_nome = ?, name = ?, db_name_key = ?, location = ?, size = ?, "
                + "pvp = ?, mobs = ?, public_access = ?, updated_at = ? WHERE id = ?";
        try {
            Connection connection = connections.get();
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, terreno.getDonoNome());
                stmt.setString(2, terreno.getName());
                stmt.setString(3, nameKey(terreno.getDonoNome(), terreno.getName()));
                stmt.setString(4, terreno.getLocation());
                stmt.setInt(5, terreno.getSize());
                stmt.setBoolean(6, terreno.getPvp());
                stmt.setBoolean(7, terreno.getMobs());
                stmt.setBoolean(8, terreno.getPublicAccess());
                stmt.setLong(9, System.currentTimeMillis());
                stmt.setLong(10, terreno.getId());

                if (stmt.executeUpdate() > 0) {
                    logger.info("Terreno atualizado: " + terreno.getId());
                    return true;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao atualizar terreno: " + terreno.getId(), e);
        }
        return false;
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
                    logger.info("Terreno deletado: " + id);
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
                    logger.info("Membro adicionado ao terreno " + terrenoId + ": " + memberNome);
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
                    logger.info("Membro removido do terreno " + terrenoId + ": " + memberNome);
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
                    logger.info("Papel do membro atualizado no terreno " + terrenoId + ": " + memberNome + " -> " + newRole);
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
            logger.log(Level.SEVERE, "Erro ao verificar existência de nome: " + nome + " para dono: " + donoNome, e);
        }
        return false;
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
