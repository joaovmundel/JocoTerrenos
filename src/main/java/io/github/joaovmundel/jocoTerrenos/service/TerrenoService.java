package io.github.joaovmundel.jocoTerrenos.service;

import io.github.joaovmundel.jocoTerrenos.economy.EconomyGateway;
import io.github.joaovmundel.jocoTerrenos.exceptions.StorageException;
import io.github.joaovmundel.jocoTerrenos.exceptions.TerrenoNotFoundException;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.protection.AllowedWorlds;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Business rules for lands. Everything here runs on the main thread: lookups hit the in-memory
 * {@link ClaimIndex}, and the few reads and writes that reach the database are single indexed statements.
 */
public class TerrenoService {

    /** Longest name a player may give a land. */
    public static final int MAX_NAME_LENGTH = 15;

    private static final String DEFAULT_MAX_SALE_PRICE = "1000000000";
    private static final int MAX_NAME_SUFFIX = 99;

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
                logger.warning("Terreno ignorado na protecao por ter localizacao ou tamanho invalidos: id=" + terreno.getId());
                continue;
            }
            claims.add(claim);
        }
        claimIndex.replaceAll(claims);
    }

    private void indexClaim(Terreno terreno) {
        Claim claim = ClaimMapper.toClaim(terreno);
        if (claim == null) {
            logger.warning("Terreno sem protecao por ter localizacao ou tamanho invalidos: id=" + terreno.getId());
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
        if (loc.getWorld() == null || !mundoPermitido(loc.getWorld().getName())) {
            return null;
        }
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

    /** Worlds where lands can be bought, from {@code lands.allowed-worlds}; every world when the list is empty. */
    public AllowedWorlds getMundosPermitidos() {
        return new AllowedWorlds(config.getStringList("lands.allowed-worlds"));
    }

    public boolean mundoPermitido(String worldName) {
        return getMundosPermitidos().allows(worldName);
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

    /** Outcome of putting a land up for sale or taking it off the market. */
    public enum SaleResult {
        OK, NOT_OWNER, NOT_LISTED, SAVE_FAILED
    }

    /** Largest price a land may be listed for ({@code lands.sale.max-price}). */
    public BigDecimal getPrecoMaximoVenda() {
        String configured = config.getString("lands.sale.max-price", DEFAULT_MAX_SALE_PRICE);
        try {
            BigDecimal max = new BigDecimal(configured.trim());
            if (max.signum() > 0) {
                return max;
            }
        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "Valor invalido em lands.sale.max-price: '" + configured + "'; usando " + DEFAULT_MAX_SALE_PRICE, e);
        }
        return new BigDecimal(DEFAULT_MAX_SALE_PRICE);
    }

    /** The amount the way the server economy shows money. */
    public String formatarPreco(BigDecimal preco) {
        return formatarPreco(preco.doubleValue());
    }

    public String formatarPreco(double preco) {
        return economy.format(preco);
    }

    /** Lists the land for sale, or changes its asking price when it already is. */
    public SaleResult colocarAVenda(String donoNome, String nome, BigDecimal preco) throws TerrenoNotFoundException {
        Terreno terreno = buscarTerrenoPorNome(donoNome, nome);
        if (!isDono(terreno, donoNome)) {
            return SaleResult.NOT_OWNER;
        }
        terreno.setSalePrice(preco);
        return saveSale(terreno);
    }

    public SaleResult cancelarVenda(String donoNome, String nome) throws TerrenoNotFoundException {
        Terreno terreno = buscarTerrenoPorNome(donoNome, nome);
        if (!isDono(terreno, donoNome)) {
            return SaleResult.NOT_OWNER;
        }
        if (terreno.getSalePrice() == null) {
            return SaleResult.NOT_LISTED;
        }
        terreno.setSalePrice(null);
        return saveSale(terreno);
    }

    private SaleResult saveSale(Terreno terreno) {
        if (!repository.update(terreno)) {
            return SaleResult.SAVE_FAILED;
        }
        indexClaim(terreno);
        return SaleResult.OK;
    }

    /** The cheapest lands that are for sale. */
    public List<Terreno> listarAVenda(int limite) {
        return repository.findForSale(limite);
    }

    /**
     * Buys a land another player put up for sale. The buyer pays the asking price to the seller, the land changes
     * hands with its members removed and becomes private, and it is no longer for sale. If the buyer already has a
     * land with the same name the new one gets a number appended.
     * <p>
     * Money and storage cannot be one transaction, so every step that fails undoes the ones before it.
     */
    public PurchaseOutcome adquirir(Player comprador, Terreno terreno) {
        BigDecimal preco = terreno.getSalePrice();
        if (preco == null) {
            return PurchaseOutcome.failure(PurchaseOutcome.Status.NOT_FOR_SALE, null);
        }
        String compradorKey = PlayerKeys.of(comprador);
        if (isDono(terreno, compradorKey)) {
            return PurchaseOutcome.failure(PurchaseOutcome.Status.OWN_LAND, preco);
        }
        if (!economy.isAvailable()) {
            return PurchaseOutcome.failure(PurchaseOutcome.Status.ECONOMY_UNAVAILABLE, preco);
        }
        double valor = preco.doubleValue();
        if (!economy.has(comprador.getName(), valor)) {
            return PurchaseOutcome.failure(PurchaseOutcome.Status.INSUFFICIENT_FUNDS, preco);
        }
        String novoNome = nomeLivreParaDono(compradorKey, terreno.getName());
        if (novoNome == null) {
            return PurchaseOutcome.failure(PurchaseOutcome.Status.NAME_IN_USE, preco);
        }

        String vendedor = terreno.getDonoNome();
        if (!economy.withdraw(comprador.getName(), valor)) {
            return PurchaseOutcome.failure(PurchaseOutcome.Status.PAYMENT_FAILED, preco);
        }
        if (!economy.deposit(vendedor, valor)) {
            if (!economy.deposit(comprador.getName(), valor)) {
                logger.severe("Falha ao devolver " + valor + " para " + comprador.getName()
                        + " depois que o pagamento a " + vendedor + " falhou. Devolva o valor manualmente.");
            }
            return PurchaseOutcome.failure(PurchaseOutcome.Status.PAYMENT_FAILED, preco);
        }

        String nomeAnterior = terreno.getName();
        terreno.setDonoNome(compradorKey);
        terreno.setName(novoNome);
        terreno.setSalePrice(null);
        terreno.setPublicAccess(false);
        if (!repository.transferOwnership(terreno)) {
            boolean recuperado = economy.withdraw(vendedor, valor);
            boolean devolvido = economy.deposit(comprador.getName(), valor);
            if (!recuperado || !devolvido) {
                logger.severe("Falha ao desfazer a venda do terreno " + terreno.getId() + ": " + valor + " de " + vendedor
                        + " para " + comprador.getName() + " (recuperado=" + recuperado + ", devolvido=" + devolvido
                        + "). Confira os saldos manualmente.");
            }
            return PurchaseOutcome.failure(PurchaseOutcome.Status.SAVE_FAILED, preco);
        }
        indexClaim(terreno);
        return PurchaseOutcome.success(terreno, preco, vendedor, nomeAnterior);
    }

    /** The name itself when the owner has no land called that, else the name with 2, 3... appended; null if all taken. */
    private String nomeLivreParaDono(String donoNome, String nome) {
        if (!repository.existsByOwnerAndName(donoNome, nome)) {
            return nome;
        }
        for (int numero = 2; numero <= MAX_NAME_SUFFIX; numero++) {
            String sufixo = String.valueOf(numero);
            String base = nome.length() + sufixo.length() > MAX_NAME_LENGTH
                    ? nome.substring(0, MAX_NAME_LENGTH - sufixo.length())
                    : nome;
            if (!repository.existsByOwnerAndName(donoNome, base + sufixo)) {
                return base + sufixo;
            }
        }
        return null;
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
