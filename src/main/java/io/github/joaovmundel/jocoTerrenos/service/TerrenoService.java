package io.github.joaovmundel.jocoTerrenos.service;

import io.github.joaovmundel.jocoTerrenos.exceptions.TerrenoNotFoundException;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.repositories.TerrenoRepository;
import io.github.joaovmundel.jocoTerrenos.utils.LocationUtils;
import io.github.joaovmundel.jocoTerrenos.utils.SafeLocationUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
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
     * Cria um novo terreno para o jogador com nome
     */
    public Optional<Terreno> criarTerreno(Player player, int tamanho, String nome) {
        // Valida tamanho
        int tamanhoMinimo = getTamanhoMinimo();
        int tamanhoMaximo = getTamanhoMaximo();
        if (tamanho < tamanhoMinimo || tamanho > tamanhoMaximo) {
            return Optional.empty();
        }

        // Valida nome
        if (nome == null) {
            return Optional.empty();
        }
        String nomeTrim = nome.trim();
        if (nomeTrim.isEmpty()) {
            return Optional.empty();
        }
        String donoUUID = player.getUniqueId().toString();
        // Unicidade case-insensitive por dono
        if (repository.existsByOwnerAndNameIgnoreCase(donoUUID, nomeTrim)) {
            return Optional.empty();
        }
        // Calcula custo e verifica saldo
        double custo = calcularCustoTerreno(tamanho);
        Economy economy = ((io.github.joaovmundel.jocoTerrenos.JocoTerrenos) Bukkit.getPluginManager().getPlugin("JocoTerrenos")).getEconomy();
        if (economy == null) {
            return Optional.empty();
        }
        if (!economy.has(player, custo)) {
            return Optional.empty();
        }
        // Debita
        if (!economy.withdrawPlayer(player, custo).transactionSuccess()) {
            return Optional.empty();
        }
        // Cria terreno
        Location loc = player.getLocation();
        String location = LocationUtils.formatarLocalizacao(loc);
        Terreno terreno = new Terreno();
        terreno.setDonoUUID(donoUUID);
        terreno.setName(nomeTrim);
        terreno.setLocation(location);
        terreno.setSize(tamanho);
        terreno.setPvp(false);
        terreno.setMobs(true);
        terreno.setPublicAccess(false);
        Optional<Terreno> created = repository.create(terreno);
        if (created.isEmpty()) {
            // Reembolso em caso de falha
            economy.depositPlayer(player, custo);
        }
        return created;
    }

    public boolean tamanhoValido(int tamanho) {
        int tamanhoMinimo = getTamanhoMinimo();
        int tamanhoMaximo = getTamanhoMaximo();
        return tamanho >= tamanhoMinimo && tamanho <= tamanhoMaximo;
    }

    public double calcularCustoTerreno(int tamanho) {
        double blockPrice = config.getDouble("terrenos.block-price", 1000.0);
        return (double) tamanho * (double) tamanho * blockPrice;
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
    public Terreno buscarTerreno(Long id) throws TerrenoNotFoundException {
        Optional<Terreno> optionalTerreno = repository.findById(id);
        return optionalTerreno.orElseThrow(() -> new TerrenoNotFoundException("Ocorreu um erro ao buscar o terreno com ID " + id + "."));
    }

    /**
     * Busca um terreno por nome
     */
    public Optional<Terreno> buscarTerrenoPorNome(String donoUUID, String nome) {
        if (nome == null) return Optional.empty();
        String key = donoUUID + "+" + nome.trim().toLowerCase();
        return repository.findByNameKey(key);
    }

    /**
     * Busca o terreno atual do jogador
     */
    public Terreno buscarTerrenoAtual(Player player) throws TerrenoNotFoundException {
        Location playerLoc = player.getLocation();
        List<Terreno> terrenos = repository.findAll();
        for (Terreno t : terrenos) {
            Location center = LocationUtils.parsearLocalizacao(t.getLocation());
            if (center == null) continue;
            int size = t.getSize();
            if (estaDentroDaArea(playerLoc, center, size)) {
                return t;
            }
        }
        throw new TerrenoNotFoundException("Nenhum terreno encontrado.");
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

    public Terreno getCurrentTerreno(Player p) throws TerrenoNotFoundException {
        List<Terreno> terrenos = repository.findAll();
        Location playerLoc = p.getLocation();
        for (Terreno t : terrenos) {
            double playerX = playerLoc.getX();
            double playerZ = playerLoc.getZ();
            Double[] boundaries = findBoundaries(t);
            if (playerX >= boundaries[0] && playerX <= boundaries[1]
                    && playerZ >= boundaries[2] && playerZ <= boundaries[3]) {
                return t;
            }
        }
        throw new TerrenoNotFoundException("Nenhum terreno encontrado.");
    }

    private Double[] findBoundaries(Terreno t) {
        Location loc = LocationUtils.parsearLocalizacao(t.getLocation());
        int size = t.getSize();
        double halfSize = size / 2.0;
        double minX = loc.getX() - halfSize;
        double maxX = loc.getX() + halfSize;
        double minZ = loc.getZ() - halfSize;
        double maxZ = loc.getZ() + halfSize;
        return new Double[]{minX, maxX, minZ, maxZ};
    }

    public Optional<Location> getSafeTeleportLocation(Long id, String requesterUUID) {
        Optional<Terreno> terrenoOpt = repository.findById(id);
        if (terrenoOpt.isEmpty()) return Optional.empty();
        Terreno terreno = terrenoOpt.get();
        // Permissões: dono ou admin
        if (!(terreno.getDonoUUID().equals(requesterUUID) || isAdminDoTerreno(id, requesterUUID))) {
            return Optional.empty();
        }
        Location center = LocationUtils.parsearLocalizacao(terreno.getLocation());
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

    private boolean estaDentroDaArea(Location point, Location center, int size) {
        double half = size / 2.0;
        if (center.getWorld() == null || point.getWorld() == null) return false;
        if (!center.getWorld().equals(point.getWorld())) return false;
        double dx = Math.abs(point.getX() - center.getX());
        double dz = Math.abs(point.getZ() - center.getZ());
        return dx <= half && dz <= half;
    }
}
