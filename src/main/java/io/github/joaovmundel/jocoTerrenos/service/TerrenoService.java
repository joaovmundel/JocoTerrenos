package io.github.joaovmundel.jocoTerrenos.service;

import io.github.joaovmundel.jocoTerrenos.exceptions.StorageException;
import io.github.joaovmundel.jocoTerrenos.exceptions.TerrenoNotFoundException;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.protection.Bounds;
import io.github.joaovmundel.jocoTerrenos.protection.Claim;
import io.github.joaovmundel.jocoTerrenos.protection.ClaimIndex;
import io.github.joaovmundel.jocoTerrenos.protection.ClaimMapper;
import io.github.joaovmundel.jocoTerrenos.repositories.TerrenoRepository;
import io.github.joaovmundel.jocoTerrenos.utils.LocationUtils;
import io.github.joaovmundel.jocoTerrenos.utils.SafeLocationUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class TerrenoService {

    private final TerrenoRepository repository;
    private final FileConfiguration config;
    private final MessageService messages;
    private final ClaimIndex claimIndex;
    private final Logger logger;

    public TerrenoService(TerrenoRepository repository, FileConfiguration config, MessageService messages,
                          ClaimIndex claimIndex, Logger logger) {
        this.repository = repository;
        this.config = config;
        this.messages = messages;
        this.claimIndex = claimIndex;
        this.logger = logger;
    }

    /**
     * Rebuilds the in-memory claim index from storage. Must succeed before the plugin protects anything:
     * an index that silently came up empty would leave every land open.
     *
     * @throws StorageException if the lands cannot be read
     */
    public void reloadClaims() throws StorageException {
        List<Claim> claims = new ArrayList<>();
        for (Terreno terreno : repository.loadAll()) {
            Claim claim = ClaimMapper.toClaim(terreno);
            if (claim == null) {
                logger.warning("Terreno ignorado na proteção por ter localização ou tamanho inválidos: id=" + terreno.getId());
                continue;
            }
            claims.add(claim);
        }
        claimIndex.replaceAll(claims);
    }

    private void indexClaim(Terreno terreno) {
        Claim claim = ClaimMapper.toClaim(terreno);
        if (claim == null) {
            logger.warning("Terreno sem proteção por ter localização ou tamanho inválidos: id=" + terreno.getId());
            return;
        }
        claimIndex.put(claim);
    }

    /**
     * Cria um terreno para o jogador com nome
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
        // Centro proposto do terreno
        Location loc = player.getLocation();
        // Verifica disponibilidade da área com espaçamento mínimo
        int buffer = getEspacoEntreTerrenos();
        if (!isAreaDisponivel(loc, tamanho, buffer)) {
            return Optional.empty();
        }
        // Calcula custo e verifica saldo
        double custo = calcularCustoTerreno(tamanho);
        Economy economy = ((io.github.joaovmundel.jocoTerrenos.JocoTerrenos) Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("JocoTerrenos"))).getEconomy();
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
        // Salva o centro do bloco: "%.2f" arredondaria 100.996 para 101.00 e a proteção usaria um bloco
        // diferente daquele em que as cercas foram colocadas (FenceUtils usa getBlockX/getBlockZ).
        Location blockCenter = new Location(loc.getWorld(), loc.getBlockX() + 0.5, loc.getY(), loc.getBlockZ() + 0.5);
        String location = LocationUtils.formatarLocalizacao(blockCenter);
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
            return created;
        }
        indexClaim(created.get());
        return created;
    }

    public boolean tamanhoValido(int tamanho) {
        int tamanhoMinimo = getTamanhoMinimo();
        int tamanhoMaximo = getTamanhoMaximo();
        return tamanho >= tamanhoMinimo && tamanho <= tamanhoMaximo;
    }

    public double calcularCustoTerreno(int tamanho) {
        double blockPrice = config.getDouble("lands.block-price", config.getDouble("terrenos.block-price", 1000.0));
        return (double) tamanho * (double) tamanho * blockPrice;
    }

    /**
     * Lista todos os terrenos de um jogador
     */
    public List<Terreno> listarTerrenosDoJogador(String playerUUID) {
        return repository.findByDonoUUID(playerUUID);
    }


    /**
     * Busca um terreno por nome
     */
    public Terreno buscarTerreno(Long id) throws TerrenoNotFoundException {
        Optional<Terreno> optionalTerreno = repository.findById(id);
        return optionalTerreno.orElseThrow(() -> new TerrenoNotFoundException(messages.get("errors.terreno.id-nao-encontrado")));
    }

    public Terreno buscarTerrenoPorNome(String donoUUID, String nome) throws TerrenoNotFoundException {
        if (nome == null || nome.trim().isEmpty())
            throw new TerrenoNotFoundException(messages.get("errors.terreno.nome-invalido"));
        String key = donoUUID + "+" + nome.trim().toLowerCase();
        return repository.findByNameKey(key).orElseThrow(() -> new TerrenoNotFoundException(messages.get("errors.terreno.nao-encontrado")));
    }

    public boolean deletarTerreno(String name, String playerUUID) throws TerrenoNotFoundException {
        Terreno terreno = buscarTerrenoPorNome(playerUUID, name);

        if (!isDono(terreno, playerUUID)) {
            return false;
        }

        boolean deleted = repository.delete(terreno.getId());
        if (deleted) {
            claimIndex.remove(terreno.getId());
        }
        return deleted;
    }

    public boolean togglePvp(String name, String playerUUID) throws TerrenoNotFoundException {
        Terreno terreno = buscarTerrenoPorNome(playerUUID, name);
        return toggleConfiguracao(terreno.getName(), playerUUID, "pvp");
    }

    public boolean toggleMobs(String nome, String playerUUID) throws TerrenoNotFoundException {
        return toggleConfiguracao(nome, playerUUID, "mobs");
    }

    public boolean togglePublico(String name, String playerUUID) throws TerrenoNotFoundException {
        return toggleConfiguracao(name, playerUUID, "publico");
    }

    public boolean isDono(String terrenoName, String playerUUID) throws TerrenoNotFoundException {
        Terreno terreno = buscarTerrenoPorNome(playerUUID, terrenoName);
        if (terreno.getDonoUUID() == null) {
            return false;
        }
        return terreno.getDonoUUID().equals(playerUUID);
    }

    public boolean isDono(Terreno terreno, String playerUUID) {
        String dono = terreno.getDonoUUID();
        return dono != null && dono.equals(playerUUID);
    }

    /**
     * Obtém o tamanho mínimo configurado
     */
    public int getTamanhoMinimo() {
        return config.getInt("lands.min-size", config.getInt("terrenos.tamanho-minimo", 5));
    }

    /**
     * Obtém o tamanho máximo configurado
     */
    public int getTamanhoMaximo() {
        return config.getInt("lands.max-size", config.getInt("terrenos.tamanho-maximo", 100));
    }

    /**
     * Distância (em blocos) mínima entre terrenos diferentes.
     */
    public int getEspacoEntreTerrenos() {
        return config.getInt("lands.spacing-between-lands", config.getInt("terrenos.espaco-entre-terrenos", 3));
    }

    /**
     * Verifica se a área proposta está livre considerando um espaçamento mínimo entre terrenos.
     */
    public boolean isAreaDisponivel(Location center, int tamanho, int buffer) {
        if (center == null || center.getWorld() == null) return false;
        Bounds area = Bounds.centeredOn(center.getBlockX(), center.getBlockZ(), tamanho);
        return claimIndex.isAreaFree(center.getWorld().getName(), area, buffer);
    }

    /**
     * Same check as {@link #isAreaDisponivel}. It no longer needs a worker thread because it only reads the
     * in-memory claim index; the future is kept so the purchase flow in the command does not change.
     */
    public CompletableFuture<Boolean> isAreaDisponivelAsync(Location center, int tamanho, int buffer) {
        return CompletableFuture.completedFuture(isAreaDisponivel(center, tamanho, buffer));
    }

    private boolean toggleConfiguracao(String nome, String playerUUID, String tipo) throws TerrenoNotFoundException {
        Terreno t = buscarTerrenoPorNome(playerUUID, nome);

        if (!isDono(t, playerUUID)) {
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

        boolean updated = repository.update(t);
        if (updated) {
            indexClaim(t);
        }
        return updated;
    }

    /**
     * Finds the land under the player. The position is resolved against the in-memory claim index (the same
     * geometry the protection uses); only the full record is read from storage, off the main thread.
     * Nota: não chama APIs Bukkit fora da main thread.
     */
    public CompletableFuture<Terreno> getCurrentTerrenoAsync(Player p) {
        // Snapshot mínimo do estado do "player" na main thread
        Location playerLoc = p.getLocation();
        String playerWorld = playerLoc.getWorld() != null ? playerLoc.getWorld().getName() : null;
        int playerX = playerLoc.getBlockX();
        int playerZ = playerLoc.getBlockZ();

        return CompletableFuture.supplyAsync(() -> {
            Claim claim = playerWorld == null ? null : claimIndex.findAt(playerWorld, playerX, playerZ);
            if (claim == null) {
                throw new RuntimeException(new TerrenoNotFoundException(messages.get("errors.terreno.nao-encontrado")));
            }
            return repository.findById(claim.getId()).orElseThrow(
                    () -> new RuntimeException(new TerrenoNotFoundException(messages.get("errors.terreno.nao-encontrado"))));
        });
    }

    public Optional<Location> getSafeTeleportLocation(String name, String requesterUUID) throws TerrenoNotFoundException {
        Terreno terreno = buscarTerrenoPorNome(requesterUUID, name);

        if (!(terreno.getDonoUUID().equals(requesterUUID))) {
            return Optional.empty();
        }

        Location center = LocationUtils.converterLocalizacao(terreno.getLocation());
        if (center == null) return Optional.empty();
        Location spot = SafeLocationUtils.findSafeSpot(center, terreno.getSize(), config);
        if (spot != null) return Optional.of(spot);
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
