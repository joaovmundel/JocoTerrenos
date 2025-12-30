package io.github.joaovmundel.jocoTerrenos.service;

import io.github.joaovmundel.jocoTerrenos.exceptions.TerrenoNotFoundException;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.repositories.TerrenoRepository;
import io.github.joaovmundel.jocoTerrenos.utils.LocationUtils;
import io.github.joaovmundel.jocoTerrenos.utils.LocationUtils.LocationRaw;
import io.github.joaovmundel.jocoTerrenos.utils.SafeLocationUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class TerrenoService {

    private final TerrenoRepository repository;
    private final FileConfiguration config;
    private final MessageService messages;

    public TerrenoService(TerrenoRepository repository, FileConfiguration config, MessageService messages) {
        this.repository = repository;
        this.config = config;
        this.messages = messages;
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

        return repository.delete(terreno.getId());
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
        return config.getInt("terrenos.tamanho-minimo", 5);
    }

    /**
     * Obtém o tamanho máximo configurado
     */
    public int getTamanhoMaximo() {
        return config.getInt("terrenos.tamanho-maximo", 100);
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

        return repository.update(t);
    }

    /**
     * Versão assíncrona que evita bloquear o thread principal.
     * Busca terrenos no repositório e calcula os limites fora da main thread.
     * Nota: não chama APIs Bukkit fora da main thread.
     */
    public CompletableFuture<Terreno> getCurrentTerrenoAsync(Player p) {
        // Snapshot mínimo do estado do "player" na main thread
        Location playerLoc = p.getLocation();
        String playerWorld = playerLoc.getWorld() != null ? playerLoc.getWorld().getName() : null;
        double playerX = playerLoc.getX();
        double playerZ = playerLoc.getZ();

        return CompletableFuture.supplyAsync(() -> {
            List<Terreno> terrenos = repository.findAll();
            for (Terreno t : terrenos) {
                LocationRaw raw = LocationUtils.converterLocalizacaoRaw(t.getLocation());
                if (raw == null || raw.worldName() == null) continue;
                if (!raw.worldName().equals(playerWorld)) continue;
                int size = t.getSize();
                double halfSize = size / 2.0;
                double minX = raw.x() - halfSize;
                double maxX = raw.x() + halfSize;
                double minZ = raw.z() - halfSize;
                double maxZ = raw.z() + halfSize;
                if (playerX >= minX && playerX <= maxX && playerZ >= minZ && playerZ <= maxZ) {
                    return t;
                }
            }
            throw new RuntimeException(new TerrenoNotFoundException(messages.get("errors.terreno.nao-encontrado")));
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
