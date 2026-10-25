package io.github.joaovmundel.jocoTerrenos.service;

import io.github.joaovmundel.jocoTerrenos.economy.EconomyGateway;
import io.github.joaovmundel.jocoTerrenos.exceptions.StorageException;
import io.github.joaovmundel.jocoTerrenos.exceptions.TerrenoNotFoundException;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.protection.Bounds;
import io.github.joaovmundel.jocoTerrenos.protection.Claim;
import io.github.joaovmundel.jocoTerrenos.protection.ClaimIndex;
import io.github.joaovmundel.jocoTerrenos.protection.ClaimMapper;
import io.github.joaovmundel.jocoTerrenos.repositories.TerrenoRepository;
import io.github.joaovmundel.jocoTerrenos.utils.LocationUtils;
import io.github.joaovmundel.jocoTerrenos.utils.PlayerKeys;
import io.github.joaovmundel.jocoTerrenos.utils.SafeLocationUtils;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Business rules for lands. Everything here runs on the main thread: lookups hit the in-memory
 * {@link ClaimIndex}, and the few reads and writes that reach the database are single indexed statements.
 */
public class TerrenoService {

    private final TerrenoRepository repository;
    private final FileConfiguration config;
    private final ClaimIndex claimIndex;
    private final EconomyGateway economy;
    private final Logger logger;

    public TerrenoService(TerrenoRepository repository, FileConfiguration config,
                          ClaimIndex claimIndex, EconomyGateway economy, Logger logger) {
        this.repository = repository;
        this.config = config;
        this.claimIndex = claimIndex;
        this.economy = economy;
        this.logger = logger;
    }

    /**
     * Rebuilds the in-memory claim index from storage. Must succeed before the plugin protects anything:
     * an index that silently came up empty would leave every land open.
     *
     * @throws StorageException if the lands cannot be read
     */
    public void reloadClaims() throws StorageException {
        List<Claim> claims = new ArrayList<Claim>();
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
     *
     * @return the new land, or {@code null} if any rule failed (size, name, free area, balance) or the
     * economy or database is unavailable
     */
    public Terreno criarTerreno(Player player, int tamanho, String nome) {
        // Valida tamanho
        if (!tamanhoValido(tamanho)) {
            return null;
        }

        // Valida nome
        if (nome == null) {
            return null;
        }
        String nomeTrim = nome.trim();
        if (nomeTrim.isEmpty()) {
            return null;
        }
        String donoNome = PlayerKeys.of(player);
        // Unicidade case-insensitive por dono
        if (repository.existsByOwnerAndName(donoNome, nomeTrim)) {
            return null;
        }
        // Centro proposto do terreno
        Location loc = player.getLocation();
        // Verifica disponibilidade da área com espaçamento mínimo
        if (!isAreaDisponivel(loc, tamanho, getEspacoEntreTerrenos())) {
            return null;
        }
        // Calcula custo e verifica saldo
        double custo = calcularCustoTerreno(tamanho);
        if (!economy.isAvailable()) {
            logger.warning("Compra de terreno recusada: nenhuma economia registrada no Vault (jogador " + player.getName() + ").");
            return null;
        }
        if (!economy.has(player.getName(), custo)) {
            return null;
        }
        // Debita
        if (!economy.withdraw(player.getName(), custo)) {
            return null;
        }
        // Salva o centro do bloco: "%.2f" arredondaria 100.996 para 101.00 e a proteção usaria um bloco
        // diferente daquele em que as cercas foram colocadas (FenceUtils usa getBlockX/getBlockZ).
        Location blockCenter = new Location(loc.getWorld(), loc.getBlockX() + 0.5, loc.getY(), loc.getBlockZ() + 0.5);
        Terreno terreno = new Terreno();
        terreno.setDonoNome(donoNome);
        terreno.setName(nomeTrim);
        terreno.setLocation(LocationUtils.formatarLocalizacao(blockCenter));
        terreno.setSize(tamanho);
        terreno.setPvp(false);
        terreno.setMobs(true);
        terreno.setPublicAccess(false);
        Terreno created = repository.create(terreno);
        if (created == null) {
            // Reembolso em caso de falha
            if (!economy.deposit(player.getName(), custo)) {
                logger.severe("Falha ao reembolsar " + custo + " para " + player.getName()
                        + " depois de um erro ao salvar o terreno '" + nomeTrim + "'. Devolva o valor manualmente.");
            }
            return null;
        }
        indexClaim(created);
        return created;
    }

    public boolean tamanhoValido(int tamanho) {
        return tamanho >= getTamanhoMinimo() && tamanho <= getTamanhoMaximo();
    }

    public double calcularCustoTerreno(int tamanho) {
        double blockPrice = config.getDouble("lands.block-price", config.getDouble("terrenos.block-price", 1000.0));
        return (double) tamanho * (double) tamanho * blockPrice;
    }

    /**
     * Lista todos os terrenos de um jogador
     */
    public List<Terreno> listarTerrenosDoJogador(String donoNome) {
        return repository.findByDono(PlayerKeys.of(donoNome));
    }

    public Terreno buscarTerrenoPorNome(String donoNome, String nome) throws TerrenoNotFoundException {
        if (nome == null || nome.trim().isEmpty()) {
            throw new TerrenoNotFoundException("land name is empty");
        }
        Terreno terreno = repository.findByNameKey(TerrenoRepository.nameKey(donoNome, nome));
        if (terreno == null) {
            throw new TerrenoNotFoundException("no land named '" + nome.trim() + "' for " + donoNome);
        }
        return terreno;
    }

    public boolean deletarTerreno(String name, String donoNome) throws TerrenoNotFoundException {
        Terreno terreno = buscarTerrenoPorNome(donoNome, name);

        if (!isDono(terreno, donoNome)) {
            return false;
        }

        boolean deleted = repository.delete(terreno.getId());
        if (deleted) {
            claimIndex.remove(terreno.getId());
        }
        return deleted;
    }

    public boolean togglePvp(String name, String donoNome) throws TerrenoNotFoundException {
        return toggleConfiguracao(name, donoNome, Setting.PVP);
    }

    public boolean toggleMobs(String nome, String donoNome) throws TerrenoNotFoundException {
        return toggleConfiguracao(nome, donoNome, Setting.MOBS);
    }

    public boolean togglePublico(String name, String donoNome) throws TerrenoNotFoundException {
        return toggleConfiguracao(name, donoNome, Setting.PUBLIC_ACCESS);
    }

    public boolean isDono(String terrenoName, String donoNome) throws TerrenoNotFoundException {
        return isDono(buscarTerrenoPorNome(donoNome, terrenoName), donoNome);
    }

    public boolean isDono(Terreno terreno, String donoNome) {
        String dono = terreno.getDonoNome();
        return dono != null && dono.equals(PlayerKeys.of(donoNome));
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

    private enum Setting {
        PVP, MOBS, PUBLIC_ACCESS
    }

    private boolean toggleConfiguracao(String nome, String donoNome, Setting setting) throws TerrenoNotFoundException {
        Terreno t = buscarTerrenoPorNome(donoNome, nome);

        if (!isDono(t, donoNome)) {
            return false;
        }

        switch (setting) {
            case PVP:
                t.setPvp(!t.getPvp());
                break;
            case MOBS:
                t.setMobs(!t.getMobs());
                break;
            case PUBLIC_ACCESS:
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
     * geometry the protection uses); only the full record is read from storage.
     *
     * @throws TerrenoNotFoundException if the player is not standing on a land
     */
    public Terreno getCurrentTerreno(Player p) throws TerrenoNotFoundException {
        Location playerLoc = p.getLocation();
        Claim claim = playerLoc.getWorld() == null ? null
                : claimIndex.findAt(playerLoc.getWorld().getName(), playerLoc.getBlockX(), playerLoc.getBlockZ());
        if (claim == null) {
            throw new TerrenoNotFoundException("player is not standing on a land");
        }
        Terreno terreno = repository.findById(claim.getId());
        if (terreno == null) {
            throw new TerrenoNotFoundException("land " + claim.getId() + " is indexed but missing from storage");
        }
        return terreno;
    }

    /**
     * @return a safe spot inside the land, or {@code null} when none was found
     */
    public Location getSafeTeleportLocation(String name, String donoNome) throws TerrenoNotFoundException {
        Terreno terreno = buscarTerrenoPorNome(donoNome, name);

        if (!isDono(terreno, donoNome)) {
            return null;
        }

        Location center = LocationUtils.converterLocalizacao(terreno.getLocation());
        if (center == null) return null;
        Location spot = SafeLocationUtils.findSafeSpot(center, terreno.getSize(), config);
        if (spot != null) return spot;
        Location retryCenter = center.clone().add(2, 0, 2);
        return SafeLocationUtils.findSafeSpot(retryCenter, terreno.getSize(), config);
    }
}
