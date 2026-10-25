package io.github.joaovmundel.jocoTerrenos.service;

import io.github.joaovmundel.jocoTerrenos.database.Schema;
import io.github.joaovmundel.jocoTerrenos.database.SqliteConnectionProvider;
import io.github.joaovmundel.jocoTerrenos.economy.EconomyGateway;
import io.github.joaovmundel.jocoTerrenos.exceptions.TerrenoNotFoundException;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.models.TerrenoRole;
import io.github.joaovmundel.jocoTerrenos.protection.Claim;
import io.github.joaovmundel.jocoTerrenos.protection.ClaimIndex;
import io.github.joaovmundel.jocoTerrenos.repositories.TerrenoRepository;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Selling and buying lands against a real SQLite file, with a fake economy so every failure path can be forced
 * and the money checked afterwards.
 */
public class TerrenoServiceSaleTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private SqliteConnectionProvider provider;
    private TerrenoRepository repository;
    private ClaimIndex index;
    private FakeEconomy economy;
    private YamlConfiguration config;
    private TerrenoService service;

    @Before
    public void setUp() throws Exception {
        Logger logger = Logger.getLogger("TerrenoServiceSaleTest");
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
        provider = new SqliteConnectionProvider(folder.newFile("terrenos.db"), logger);
        Schema.create(provider.get(), Schema.Dialect.SQLITE);
        repository = new TerrenoRepository(provider, logger);
        index = new ClaimIndex();
        economy = new FakeEconomy();
        config = new YamlConfiguration();
        service = new TerrenoService(repository, config, index, economy, logger);
    }

    @After
    public void tearDown() {
        provider.close();
    }

    // ---------------------------------------------------------------- helpers

    private Terreno land(String owner, String name) throws Exception {
        Terreno terreno = new Terreno();
        terreno.setDonoNome(owner);
        terreno.setName(name);
        terreno.setLocation("world:100.50:64.00:200.50");
        terreno.setSize(10);
        terreno.setPvp(false);
        terreno.setMobs(true);
        terreno.setPublicAccess(false);
        Terreno created = repository.create(terreno);
        assertNotNull(created);
        service.reloadClaims();
        return created;
    }

    private static Player player(final String name) {
        return (Player) Proxy.newProxyInstance(TerrenoServiceSaleTest.class.getClassLoader(), new Class<?>[]{Player.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if (method.getName().equals("getName")) {
                            return name;
                        }
                        if (method.getName().equals("equals")) {
                            return proxy == args[0];
                        }
                        if (method.getName().equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        }
                        return null;
                    }
                });
    }

    private static final BigDecimal PRICE = new BigDecimal("5000.00");

    /** Balances by lowercase name, with switches to make each operation fail. */
    private static final class FakeEconomy implements EconomyGateway {
        final Map<String, Double> balances = new HashMap<String, Double>();
        final Set<String> failWithdrawFor = new HashSet<String>();
        final Set<String> failDepositFor = new HashSet<String>();
        boolean available = true;

        double balanceOf(String name) {
            Double value = balances.get(name.toLowerCase(Locale.ENGLISH));
            return value == null ? 0 : value;
        }

        void give(String name, double amount) {
            balances.put(name.toLowerCase(Locale.ENGLISH), amount);
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean has(String playerName, double amount) {
            return available && balanceOf(playerName) >= amount;
        }

        @Override
        public boolean withdraw(String playerName, double amount) {
            if (!available || failWithdrawFor.contains(playerName.toLowerCase(Locale.ENGLISH)) || balanceOf(playerName) < amount) {
                return false;
            }
            give(playerName, balanceOf(playerName) - amount);
            return true;
        }

        @Override
        public boolean deposit(String playerName, double amount) {
            if (!available || failDepositFor.contains(playerName.toLowerCase(Locale.ENGLISH))) {
                return false;
            }
            give(playerName, balanceOf(playerName) + amount);
            return true;
        }

        @Override
        public String format(double amount) {
            return String.format(Locale.ENGLISH, "$%.2f", amount);
        }
    }

    // ---------------------------------------------------------------- listing

    @Test
    public void listingStoresThePriceAndMarksTheClaimForSale() throws Exception {
        Terreno casa = land("joao", "Casa");

        assertEquals(TerrenoService.SaleResult.OK, service.colocarAVenda("Joao", "casa", PRICE));

        assertEquals(PRICE, repository.findById(casa.getId()).getSalePrice());
        assertTrue(index.findById(casa.getId()).isForSale());
    }

    @Test
    public void listingAgainJustChangesThePrice() throws Exception {
        Terreno casa = land("joao", "Casa");
        service.colocarAVenda("joao", "Casa", PRICE);

        service.colocarAVenda("joao", "Casa", new BigDecimal("7500.50"));

        assertEquals(new BigDecimal("7500.50"), repository.findById(casa.getId()).getSalePrice());
    }

    @Test(expected = TerrenoNotFoundException.class)
    public void listingAnUnknownLandIsNotFound() throws Exception {
        service.colocarAVenda("joao", "fantasma", PRICE);
    }

    @Test
    public void onlyTheOwnersLandsCanBeListed() throws Exception {
        land("maria", "Sitio");

        // a land is looked up by owner and name, so another player cannot even reach it
        try {
            service.colocarAVenda("joao", "Sitio", PRICE);
            org.junit.Assert.fail("expected the land not to be found for another player");
        } catch (TerrenoNotFoundException expected) {
            assertNull(repository.findByNameKey(TerrenoRepository.nameKey("maria", "Sitio")).getSalePrice());
        }
    }

    @Test
    public void cancellingClearsThePriceAndTheFlag() throws Exception {
        Terreno casa = land("joao", "Casa");
        service.colocarAVenda("joao", "Casa", PRICE);

        assertEquals(TerrenoService.SaleResult.OK, service.cancelarVenda("joao", "Casa"));

        assertNull(repository.findById(casa.getId()).getSalePrice());
        assertFalse(index.findById(casa.getId()).isForSale());
    }

    @Test
    public void cancellingALandThatIsNotListedSaysSo() throws Exception {
        land("joao", "Casa");

        assertEquals(TerrenoService.SaleResult.NOT_LISTED, service.cancelarVenda("joao", "Casa"));
    }

    @Test
    public void theMarketShowsTheCheapestFirst() throws Exception {
        land("joao", "Cara");
        land("joao", "Barata");
        land("joao", "NaoVende");
        service.colocarAVenda("joao", "Cara", new BigDecimal("900.00"));
        service.colocarAVenda("joao", "Barata", new BigDecimal("100.00"));

        java.util.List<Terreno> market = service.listarAVenda(10);

        assertEquals(2, market.size());
        assertEquals("Barata", market.get(0).getName());
        assertEquals("Cara", market.get(1).getName());
        assertEquals(1, service.listarAVenda(1).size());
    }

    // ---------------------------------------------------------------- buying

    @Test
    public void aPurchaseMovesTheMoneyAndTheLand() throws Exception {
        Terreno casa = land("joao", "Casa");
        repository.addMember(casa.getId(), "pedro", TerrenoRole.MEMBER);
        service.reloadClaims();
        service.colocarAVenda("joao", "Casa", PRICE);
        economy.give("joao", 100);
        economy.give("maria", 6000);

        PurchaseOutcome outcome = service.adquirir(player("Maria"), service.buscarTerrenoPorNome("joao", "Casa"));

        assertEquals(PurchaseOutcome.Status.SUCCESS, outcome.getStatus());
        assertEquals(1000.0, economy.balanceOf("maria"), 0.001);
        assertEquals(5100.0, economy.balanceOf("joao"), 0.001);
        assertEquals("joao", outcome.getPreviousOwner());
        assertFalse(outcome.wasRenamed());

        Terreno stored = repository.findById(casa.getId());
        assertEquals("maria", stored.getDonoNome());
        assertNull("no longer for sale", stored.getSalePrice());
        assertEquals("the land starts like any new one", TerrenoService.DEFAULT_PUBLIC_ACCESS, stored.getPublicAccess());
        assertTrue("the old owner's members do not follow the land", stored.getMembers().isEmpty());

        Claim claim = index.findById(casa.getId());
        assertEquals("maria", claim.getOwnerKey());
        assertFalse(claim.isForSale());
        assertFalse(claim.allowsBuild("joao"));
        assertFalse(claim.allowsBuild("pedro"));
        assertTrue(claim.allowsBuild("maria"));
    }

    @Test
    public void notEnoughMoneyChangesNothing() throws Exception {
        Terreno casa = land("joao", "Casa");
        service.colocarAVenda("joao", "Casa", PRICE);
        economy.give("maria", 4999.99);

        PurchaseOutcome outcome = service.adquirir(player("maria"), service.buscarTerrenoPorNome("joao", "Casa"));

        assertEquals(PurchaseOutcome.Status.INSUFFICIENT_FUNDS, outcome.getStatus());
        assertEquals(4999.99, economy.balanceOf("maria"), 0.001);
        assertEquals("joao", repository.findById(casa.getId()).getDonoNome());
    }

    @Test
    public void youCannotBuyYourOwnLand() throws Exception {
        land("joao", "Casa");
        service.colocarAVenda("joao", "Casa", PRICE);
        economy.give("joao", 9999);

        PurchaseOutcome outcome = service.adquirir(player("JOAO"), service.buscarTerrenoPorNome("joao", "Casa"));

        assertEquals(PurchaseOutcome.Status.OWN_LAND, outcome.getStatus());
        assertEquals(9999.0, economy.balanceOf("joao"), 0.001);
    }

    @Test
    public void aLandThatIsNotForSaleCannotBeBought() throws Exception {
        land("joao", "Casa");
        economy.give("maria", 9999);

        PurchaseOutcome outcome = service.adquirir(player("maria"), service.buscarTerrenoPorNome("joao", "Casa"));

        assertEquals(PurchaseOutcome.Status.NOT_FOR_SALE, outcome.getStatus());
        assertEquals(9999.0, economy.balanceOf("maria"), 0.001);
    }

    @Test
    public void withoutAnEconomyNothingHappens() throws Exception {
        Terreno casa = land("joao", "Casa");
        service.colocarAVenda("joao", "Casa", PRICE);
        economy.available = false;

        PurchaseOutcome outcome = service.adquirir(player("maria"), service.buscarTerrenoPorNome("joao", "Casa"));

        assertEquals(PurchaseOutcome.Status.ECONOMY_UNAVAILABLE, outcome.getStatus());
        assertEquals("joao", repository.findById(casa.getId()).getDonoNome());
    }

    @Test
    public void aFailedWithdrawalChargesNothingAndKeepsTheLand() throws Exception {
        Terreno casa = land("joao", "Casa");
        service.colocarAVenda("joao", "Casa", PRICE);
        economy.give("maria", 6000);
        economy.failWithdrawFor.add("maria");

        PurchaseOutcome outcome = service.adquirir(player("maria"), service.buscarTerrenoPorNome("joao", "Casa"));

        assertEquals(PurchaseOutcome.Status.PAYMENT_FAILED, outcome.getStatus());
        assertEquals(6000.0, economy.balanceOf("maria"), 0.001);
        assertEquals("joao", repository.findById(casa.getId()).getDonoNome());
        assertTrue(repository.findById(casa.getId()).getSalePrice() != null);
    }

    @Test
    public void ifTheSellerCannotBePaidTheBuyerGetsTheMoneyBack() throws Exception {
        Terreno casa = land("joao", "Casa");
        service.colocarAVenda("joao", "Casa", PRICE);
        economy.give("maria", 6000);
        economy.failDepositFor.add("joao");

        PurchaseOutcome outcome = service.adquirir(player("maria"), service.buscarTerrenoPorNome("joao", "Casa"));

        assertEquals(PurchaseOutcome.Status.PAYMENT_FAILED, outcome.getStatus());
        assertEquals(6000.0, economy.balanceOf("maria"), 0.001);
        assertEquals("joao", repository.findById(casa.getId()).getDonoNome());
    }

    @Test
    public void ifTheLandCannotBeSavedThePaymentIsReversed() throws Exception {
        Terreno casa = land("joao", "Casa");
        service.colocarAVenda("joao", "Casa", PRICE);
        economy.give("maria", 6000);
        economy.give("joao", 0);
        Terreno snapshot = service.buscarTerrenoPorNome("joao", "Casa");
        repository.delete(casa.getId()); // the row vanishes between the lookup and the transfer

        PurchaseOutcome outcome = service.adquirir(player("maria"), snapshot);

        assertEquals(PurchaseOutcome.Status.SAVE_FAILED, outcome.getStatus());
        assertEquals(6000.0, economy.balanceOf("maria"), 0.001);
        assertEquals(0.0, economy.balanceOf("joao"), 0.001);
    }

    // ---------------------------------------------------------------- names

    @Test
    public void aNameClashGetsANumberAppended() throws Exception {
        land("maria", "Casa");
        land("joao", "Casa");
        service.colocarAVenda("joao", "Casa", PRICE);
        economy.give("maria", 6000);

        PurchaseOutcome outcome = service.adquirir(player("maria"), service.buscarTerrenoPorNome("joao", "Casa"));

        assertEquals(PurchaseOutcome.Status.SUCCESS, outcome.getStatus());
        assertTrue(outcome.wasRenamed());
        assertEquals("Casa2", outcome.getTerreno().getName());
        assertNotNull(repository.findByNameKey(TerrenoRepository.nameKey("maria", "Casa2")));
        assertNotNull("her own land keeps its name", repository.findByNameKey(TerrenoRepository.nameKey("maria", "Casa")));
    }

    @Test
    public void numbersKeepCountingWhenSeveralAreTaken() throws Exception {
        land("maria", "Casa");
        land("maria", "Casa2");
        land("joao", "Casa");
        service.colocarAVenda("joao", "Casa", PRICE);
        economy.give("maria", 6000);

        PurchaseOutcome outcome = service.adquirir(player("maria"), service.buscarTerrenoPorNome("joao", "Casa"));

        assertEquals("Casa3", outcome.getTerreno().getName());
    }

    @Test
    public void theNumberedNameStillFitsTheLimit() throws Exception {
        String longName = "123456789012345"; // exactly the 15 allowed
        land("maria", longName);
        land("joao", longName);
        service.colocarAVenda("joao", longName, PRICE);
        economy.give("maria", 6000);

        PurchaseOutcome outcome = service.adquirir(player("maria"), service.buscarTerrenoPorNome("joao", longName));

        // the last character makes room for the "2"
        assertEquals("123456789012342", outcome.getTerreno().getName());
        assertTrue(outcome.getTerreno().getName().length() <= TerrenoService.MAX_NAME_LENGTH);
    }

    // ---------------------------------------------------------------- settings

    @Test
    public void theMaximumPriceDefaultsAndCanBeConfigured() {
        assertEquals(new BigDecimal("1000000000"), service.getPrecoMaximoVenda());

        config.set("lands.sale.max-price", 250000);
        assertEquals(new BigDecimal("250000"), service.getPrecoMaximoVenda());
    }

    @Test
    public void aBrokenMaximumPriceFallsBackToTheDefault() {
        config.set("lands.sale.max-price", "lots");
        assertEquals(new BigDecimal("1000000000"), service.getPrecoMaximoVenda());

        config.set("lands.sale.max-price", -5);
        assertEquals(new BigDecimal("1000000000"), service.getPrecoMaximoVenda());
    }

    @Test
    public void pricesAreShownWithTheEconomyFormat() {
        assertEquals("$5000.00", service.formatarPreco(PRICE));
    }
}
