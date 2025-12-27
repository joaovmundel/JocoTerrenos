package io.github.joaovmundel.jocoTerrenos.service;

import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.repositories.TerrenoRepository;
import io.github.joaovmundel.jocoTerrenos.utils.SafeLocationUtils;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class TerrenoService {

    private final TerrenoRepository repository;
    private final FileConfiguration config;

    public TerrenoService(TerrenoRepository repository, FileConfiguration config) {
        this.repository = repository;
        this.config = config;
    }

    /**
     * Cria um novo terreno para o jogador
     */
    public Optional<Terreno> criarTerreno(Player player, int tamanho) {
        // Valida tamanho mínimo
        int tamanhoMinimo = config.getInt("terrenos.tamanho-minimo", 5);
        if (tamanho < tamanhoMinimo) {
            return Optional.empty();
        }

        // Valida tamanho máximo
        int tamanhoMaximo = config.getInt("terrenos.tamanho-maximo", 100);
        if (tamanho > tamanhoMaximo) {
            return Optional.empty();
        }

        Location loc = player.getLocation();
        String location = formatarLocalizacao(loc);

        Terreno terreno = new Terreno();
        terreno.setDonoUUID(player.getUniqueId().toString());
        terreno.setLocation(location);
        terreno.setSize(tamanho);
        terreno.setPvp(false);
        terreno.setMobs(true);
        terreno.setPublicAccess(false);

        return repository.create(terreno);
    }

    /**
     * Lista todos os terrenos de um jogador
     */
    public List<Terreno> listarTerrenosDoJogador(String playerUUID) {
        return repository.findByDonoUUID(playerUUID);
    }

    /**
     * Busca um terreno por ID
     */
    public Optional<Terreno> buscarTerreno(Long id) {
        return repository.findById(id);
    }

    /**
     * Deleta um terreno (apenas se o jogador for o dono)
     */
    public boolean deletarTerreno(Long id, String playerUUID) {
        Optional<Terreno> terreno = repository.findById(id);

        if (terreno.isEmpty()) {
            return false;
        }

        if (!terreno.get().getDonoUUID().equals(playerUUID)) {
            return false;
        }

        return repository.delete(id);
    }

    /**
     * Alterna o PvP do terreno
     */
    public boolean togglePvp(Long id, String playerUUID) {
        return toggleConfiguracao(id, playerUUID, "pvp");
    }

    /**
     * Alterna os Mobs do terreno
     */
    public boolean toggleMobs(Long id, String playerUUID) {
        return toggleConfiguracao(id, playerUUID, "mobs");
    }

    /**
     * Alterna o acesso público do terreno
     */
    public boolean togglePublico(Long id, String playerUUID) {
        return toggleConfiguracao(id, playerUUID, "publico");
    }

    /**
     * Verifica se um jogador é dono de um terreno
     */
    public boolean isDono(Long terrenoId, String playerUUID) {
        Optional<Terreno> terreno = repository.findById(terrenoId);
        return terreno.isPresent() && terreno.get().getDonoUUID().equals(playerUUID);
    }

    /**
     * Obtém o tamanho mínimo configurado
     */
    public int getTamanhoMinimo() {
        return config.getInt("terrenos.tamanho-minimo", 5);
    }

    /**
     * Obtém o tamanho máximo configurado
     */
    public int getTamanhoMaximo() {
        return config.getInt("terrenos.tamanho-maximo", 100);
    }

    /**
     * Método privado para alternar configurações do terreno
     */
    private boolean toggleConfiguracao(Long id, String playerUUID, String tipo) {
        Optional<Terreno> terreno = repository.findById(id);

        if (terreno.isEmpty()) {
            return false;
        }

        Terreno t = terreno.get();

        if (!t.getDonoUUID().equals(playerUUID)) {
            return false;
        }

        switch (tipo) {
            case "pvp":
                t.setPvp(!t.getPvp());
                break;
            case "mobs":
                t.setMobs(!t.getMobs());
                break;
            case "publico":
                t.setPublicAccess(!t.getPublicAccess());
                break;
            default:
                return false;
        }

        return repository.update(t);
    }

    /**
     * Formata a localização para string
     */
    private String formatarLocalizacao(Location loc) {
        return String.format("%s:%.2f:%.2f:%.2f",
                loc.getWorld() != null ? loc.getWorld().getName() : "world",
                loc.getX(),
                loc.getY(),
                loc.getZ()
        );
    }

    /**
     * Parseia uma string de localização para objeto Location
     * Formato esperado: "world:x:y:z"
     */
    public Location parsearLocalizacao(String locationStr) {
        if (locationStr == null || locationStr.isEmpty()) {
            return null;
        }

        try {
            String[] parts = locationStr.split(":");
            if (parts.length != 4) {
                return null;
            }

            String worldName = parts[0];
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);

            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) {
                return null;
            }

            return new Location(world, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }

    public Optional<Location> getSafeTeleportLocation(Long id, String requesterUUID) {
        Optional<Terreno> terrenoOpt = repository.findById(id);
        if (terrenoOpt.isEmpty()) return Optional.empty();
        Terreno terreno = terrenoOpt.get();
        // Permissões: dono ou admin
        if (!(terreno.getDonoUUID().equals(requesterUUID) || isAdminDoTerreno(id, requesterUUID))) {
            return Optional.empty();
        }
        Location center = parsearLocalizacao(terreno.getLocation());
        if (center == null) return Optional.empty();
        Location spot = SafeLocationUtils.findSafeSpot(center, terreno.getSize(), config);
        if (spot != null) return Optional.of(spot);
        // Single retry with small offset around center
        Location retryCenter = center.clone().add(2, 0, 2);
        spot = SafeLocationUtils.findSafeSpot(retryCenter, terreno.getSize(), config);
        return Optional.ofNullable(spot);
    }

    public boolean isAdminDoTerreno(Long id, String playerUUID) {
        Optional<Terreno> tOpt = repository.findById(id);
        if (tOpt.isEmpty()) return false;
        Terreno t = tOpt.get();
        return t.getMembers().stream().anyMatch(m -> playerUUID.equals(m.getMemberUUID()) && m.getMemberRole() != null && m.getMemberRole().name().equalsIgnoreCase("ADMIN"));
    }
}
