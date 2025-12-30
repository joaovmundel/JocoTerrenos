package io.github.joaovmundel.jocoTerrenos.repositories;

import io.github.joaovmundel.jocoTerrenos.database.DatabaseManager;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.models.TerrenoMember;
import io.github.joaovmundel.jocoTerrenos.models.TerrenoRole;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TerrenoRepository {

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public TerrenoRepository(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    /**
     * Cria um novo terreno no banco de dados
     */
    public Optional<Terreno> create(Terreno terreno) {
        String sql = """
                    INSERT INTO terrenos (dono_uuid, name, db_name_key, location, size, pvp, mobs, public_access)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, terreno.getDonoUUID());
            stmt.setString(2, terreno.getName());
            // db_name_key = ownerUUID + "+" + lower(name)
            stmt.setString(3, terreno.getDonoUUID() + "+" + terreno.getName().toLowerCase());
            stmt.setString(4, terreno.getLocation());
            stmt.setInt(5, terreno.getSize());
            stmt.setBoolean(6, terreno.getPvp());
            stmt.setBoolean(7, terreno.getMobs());
            stmt.setBoolean(8, terreno.getPublicAccess());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        terreno.setId(generatedKeys.getLong(1));
                        logger.info("Terreno criado com ID: " + terreno.getId());
                        return Optional.of(terreno);
                    }
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao criar terreno", e);
        }

        return Optional.empty();
    }

    /**
     * Busca um terreno pelo ID
     */
    public Optional<Terreno> findById(Long id) {
        String sql = "SELECT * FROM terrenos WHERE id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Terreno terreno = mapResultSetToTerreno(rs);
                    // Carrega os membros do terreno
                    terreno.setMembers(findMembersByTerrenoId(id));
                    return Optional.of(terreno);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao buscar terreno por ID: " + id, e);
        }

        return Optional.empty();
    }

    /**
     * Busca todos os terrenos de um dono (UUID)
     */
    public List<Terreno> findByDonoUUID(String donoUUID) {
        String sql = "SELECT * FROM terrenos WHERE dono_uuid = ?";
        List<Terreno> terrenos = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, donoUUID);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Terreno terreno = mapResultSetToTerreno(rs);
                    terreno.setMembers(findMembersByTerrenoId(terreno.getId()));
                    terrenos.add(terreno);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao buscar terrenos do dono: " + donoUUID, e);
        }

        return terrenos;
    }

    public Terreno findByName(String ownerUUID, String name) {
        String sql = "SELECT * FROM terrenos WHERE name = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, ownerUUID + "+" + name);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Terreno terreno = mapResultSetToTerreno(rs);
                    terreno.setMembers(findMembersByTerrenoId(terreno.getId()));
                    return terreno;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao buscar terreno por nome: " + name, e);
        }

        return null;
    }


    /**
     * Busca todos os terrenos
     */
    public List<Terreno> findAll() {
        String sql = "SELECT * FROM terrenos";
        List<Terreno> terrenos = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Terreno terreno = mapResultSetToTerreno(rs);
                terreno.setMembers(findMembersByTerrenoId(terreno.getId()));
                terrenos.add(terreno);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao buscar todos os terrenos", e);
        }

        return terrenos;
    }

    /**
     * Atualiza um terreno existente
     */
    public boolean update(Terreno terreno) {
        String sql = """
                    UPDATE terrenos
                    SET dono_uuid = ?, name = ?, db_name_key = ?, location = ?, size = ?, pvp = ?, mobs = ?, public_access = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, terreno.getDonoUUID());
            stmt.setString(2, terreno.getName());
            stmt.setString(3, terreno.getDonoUUID() + "+" + terreno.getName().toLowerCase());
            stmt.setString(4, terreno.getLocation());
            stmt.setInt(5, terreno.getSize());
            stmt.setBoolean(6, terreno.getPvp());
            stmt.setBoolean(7, terreno.getMobs());
            stmt.setBoolean(8, terreno.getPublicAccess());
            stmt.setLong(9, terreno.getId());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Terreno atualizado: " + terreno.getId());
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao atualizar terreno: " + terreno.getId(), e);
        }

        return false;
    }

    /**
     * Deleta um terreno pelo ID
     */
    public boolean delete(Long id) {
        String sql = "DELETE FROM terrenos WHERE id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Terreno deletado: " + id);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao deletar terreno: " + id, e);
        }

        return false;
    }

    public boolean deleteByName(String ownerUUID, String name) {
        String sql = "DELETE FROM terrenos WHERE id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, ownerUUID + "+" + name);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Terreno deletado: " + name);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao deletar terreno: " + name, e);
        }

        return false;
    }

    /**
     * Adiciona um membro ao terreno
     */
    public boolean addMember(Long terrenoId, String memberUUID, TerrenoRole role) {
        String sql = """
                    INSERT INTO terreno_members (terreno_id, member_uuid, member_role)
                    VALUES (?, ?, ?)
                """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, terrenoId);
            stmt.setString(2, memberUUID);
            stmt.setString(3, role.name());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Membro adicionado ao terreno " + terrenoId + ": " + memberUUID);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao adicionar membro ao terreno: " + terrenoId, e);
        }

        return false;
    }

    /**
     * Remove um membro do terreno
     */
    public boolean removeMember(Long terrenoId, String memberUUID) {
        String sql = "DELETE FROM terreno_members WHERE terreno_id = ? AND member_uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, terrenoId);
            stmt.setString(2, memberUUID);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Membro removido do terreno " + terrenoId + ": " + memberUUID);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao remover membro do terreno: " + terrenoId, e);
        }

        return false;
    }

    /**
     * Atualiza o papel de um membro no terreno
     */
    public boolean updateMemberRole(Long terrenoId, String memberUUID, TerrenoRole newRole) {
        String sql = "UPDATE terreno_members SET member_role = ? WHERE terreno_id = ? AND member_uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newRole.name());
            stmt.setLong(2, terrenoId);
            stmt.setString(3, memberUUID);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Papel do membro atualizado no terreno " + terrenoId + ": " + memberUUID + " -> " + newRole);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao atualizar papel do membro: " + terrenoId, e);
        }

        return false;
    }

    /**
     * Busca todos os membros de um terreno
     */
    public List<TerrenoMember> findMembersByTerrenoId(Long terrenoId) {
        String sql = "SELECT * FROM terreno_members WHERE terreno_id = ?";
        List<TerrenoMember> members = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, terrenoId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TerrenoMember member = new TerrenoMember();
                    member.setTerrenoId(rs.getLong("terreno_id"));
                    member.setMemberUUID(rs.getString("member_uuid"));
                    member.setMemberRole(TerrenoRole.valueOf(rs.getString("member_role")));
                    members.add(member);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao buscar membros do terreno: " + terrenoId, e);
        }

        return members;
    }

    /**
     * Busca terrenos onde o jogador é membro
     */
    public List<Terreno> findTerrenosByMemberUUID(String memberUUID) {
        String sql = """
                    SELECT t.* FROM terrenos t
                    INNER JOIN terreno_members tm ON t.id = tm.terreno_id
                    WHERE tm.member_uuid = ?
                """;
        List<Terreno> terrenos = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, memberUUID);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Terreno terreno = mapResultSetToTerreno(rs);
                    terreno.setMembers(findMembersByTerrenoId(terreno.getId()));
                    terrenos.add(terreno);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao buscar terrenos do membro: " + memberUUID, e);
        }

        return terrenos;
    }

    /**
     * Busca um terreno pelo nomeKey (chave de nome única)
     */
    public Optional<Terreno> findByNameKey(String dbNameKey) {
        String sql = "SELECT * FROM terrenos WHERE db_name_key = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, dbNameKey);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Terreno terreno = mapResultSetToTerreno(rs);
                    terreno.setMembers(findMembersByTerrenoId(terreno.getId()));
                    return Optional.of(terreno);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao buscar terreno por nomeKey: " + dbNameKey, e);
        }

        return Optional.empty();
    }

    /**
     * Verifica se um terreno com o mesmo dono e nome (ignorando maiúsculas/minúsculas) já existe
     */
    public boolean existsByOwnerAndNameIgnoreCase(String donoUUID, String nome) {
        String sql = "SELECT COUNT(1) FROM terrenos WHERE dono_uuid = ? AND LOWER(name) = LOWER(?)";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, donoUUID);
            stmt.setString(2, nome);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao verificar existência de nome: " + nome + " para dono: " + donoUUID, e);
        }

        return false;
    }

    /**
     * Mapeia um ResultSet para um objeto Terreno
     */
    private Terreno mapResultSetToTerreno(ResultSet rs) throws SQLException {
        Terreno terreno = new Terreno();
        terreno.setId(rs.getLong("id"));
        terreno.setDonoUUID(rs.getString("dono_uuid"));
        terreno.setName(rs.getString("name"));
        terreno.setLocation(rs.getString("location"));
        terreno.setSize(rs.getInt("size"));
        terreno.setPvp(rs.getBoolean("pvp"));
        terreno.setMobs(rs.getBoolean("mobs"));
        terreno.setPublicAccess(rs.getBoolean("public_access"));
        return terreno;
    }
}

