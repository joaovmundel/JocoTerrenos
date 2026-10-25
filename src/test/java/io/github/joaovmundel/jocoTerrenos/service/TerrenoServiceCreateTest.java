package io.github.joaovmundel.jocoTerrenos.service;

import io.github.joaovmundel.jocoTerrenos.database.Schema;
import io.github.joaovmundel.jocoTerrenos.database.SqliteConnectionProvider;
import io.github.joaovmundel.jocoTerrenos.economy.EconomyGateway;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.protection.ClaimIndex;
import io.github.joaovmundel.jocoTerrenos.repositories.TerrenoRepository;
import org.bukkit.Location;
import org.bukkit.World;
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
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Buying a new land from the server, end to end without a server: a fake player standing in a fake world, a fake
 * economy and a real SQLite file.
 */
public class TerrenoServiceCreateTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private SqliteConnectionProvider provider;
    private TerrenoRepository repository;
    private ClaimIndex index;
    private YamlConfiguration config;
    private TerrenoService service;
    private double balance;
    private boolean economyAvailable;

    @Before
    public void setUp() throws Exception {
        Logger logger = Logger.getLogger("TerrenoServiceCreateTest");
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
        provider = new SqliteConnectionProvider(folder.newFile("terrenos.db"), logger);
        Schema.create(provider.get(), Schema.Dialect.SQLITE);
        repository = new TerrenoRepository(provider, logger);
        index = new ClaimIndex();
        config = new YamlConfiguration();
        balance = 1000000;
        economyAvailable = true;
        service = new TerrenoService(repository, config, index, new EconomyGateway() {
            @Override
            public boolean isAvailable() {
                return economyAvailable;
            }

            @Override
            public boolean has(String playerName, double amount) {
                return economyAvailable && balance >= amount;
            }

            @Override
            public boolean withdraw(String playerName, double amount) {
                if (!has(playerName, amount)) {
                    return false;
                }
                balance -= amount;
                return true;
            }

            @Override
            public boolean deposit(String playerName, double amount) {
                balance += amount;
                return true;
            }

            @Override
            public String format(double amount) {
                return String.valueOf(amount);
            }
        }, logger);
    }

    @After
    public void tearDown() {
        provider.close();
    }

    private static World world(final String name) {
        return (World) Proxy.newProxyInstance(TerrenoServiceCreateTest.class.getClassLoader(), new Class<?>[]{World.class},
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

    private static Player playerAt(final String name, final World world, final double x, final double z) {
        return (Player) Proxy.newProxyInstance(TerrenoServiceCreateTest.class.getClassLoader(), new Class<?>[]{Player.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if (method.getName().equals("getName")) {
                            return name;
                        }
                        if (method.getName().equals("getLocation")) {
                            return new Location(world, x, 64, z);
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

    @Test
    public void aLandIsCreatedAndChargedInAnAllowedWorld() throws Exception {
        config.set("lands.allowed-worlds", Arrays.asList("world"));

        Terreno created = service.criarTerreno(playerAt("Joao", world("world"), 100.7, 200.2), 10, "Casa");

        assertNotNull(created);
        assertEquals("joao", created.getDonoNome());
        assertEquals(1000000 - 100000.0, balance, 0.001);
        assertNotNull(repository.findById(created.getId()));
        assertEquals(created.getId().longValue(), index.findAt("world", 100, 200).getId());
    }

    @Test
    public void nothingIsBoughtOrChargedInAWorldThatIsNotListed() throws Exception {
        config.set("lands.allowed-worlds", Arrays.asList("world", "plano"));

        Terreno created = service.criarTerreno(playerAt("Joao", world("world_nether"), 10, 10), 10, "Casa");

        assertNull(created);
        assertEquals(1000000.0, balance, 0.001);
        assertEquals(0, index.size());
        assertTrue(repository.findByDono("joao").isEmpty());
    }

    @Test
    public void worldNamesAreComparedIgnoringCase() {
        config.set("lands.allowed-worlds", Arrays.asList("World"));

        assertNotNull(service.criarTerreno(playerAt("Joao", world("world"), 10, 10), 10, "Casa"));
    }

    @Test
    public void withoutTheSettingEveryWorldIsAllowed() {
        assertNotNull(service.criarTerreno(playerAt("Joao", world("qualquer_mundo"), 10, 10), 10, "Casa"));
    }

    @Test
    public void anEmptyListMeansEveryWorld() {
        config.set("lands.allowed-worlds", Arrays.<String>asList());

        assertNotNull(service.criarTerreno(playerAt("Joao", world("world_nether"), 10, 10), 10, "Casa"));
    }

    @Test
    public void notEnoughMoneyBuysNothing() {
        balance = 99999.99;

        assertNull(service.criarTerreno(playerAt("Joao", world("world"), 10, 10), 10, "Casa"));
        assertEquals(99999.99, balance, 0.001);
        assertEquals(0, index.size());
    }

    @Test
    public void withoutAnEconomyNothingIsBought() {
        economyAvailable = false;

        assertNull(service.criarTerreno(playerAt("Joao", world("world"), 10, 10), 10, "Casa"));
        assertEquals(0, index.size());
    }

    @Test
    public void theSameOwnerCannotReuseAName() {
        assertNotNull(service.criarTerreno(playerAt("Joao", world("world"), 10, 10), 10, "Casa"));
        double afterFirst = balance;

        assertNull(service.criarTerreno(playerAt("JOAO", world("world"), 500, 500), 10, "casa"));
        assertEquals(afterFirst, balance, 0.001);
    }

    @Test
    public void aLandCannotBeBoughtOnTopOfAnother() {
        assertNotNull(service.criarTerreno(playerAt("Joao", world("world"), 10, 10), 10, "Casa"));
        double afterFirst = balance;

        assertNull(service.criarTerreno(playerAt("Maria", world("world"), 12, 12), 10, "Sitio"));
        assertEquals(afterFirst, balance, 0.001);
        assertFalse(service.isAreaDisponivel(new Location(world("world"), 12, 64, 12), 10, 3));
    }

    @Test
    public void sizesOutsideTheLimitsAreRefused() {
        assertNull(service.criarTerreno(playerAt("Joao", world("world"), 10, 10), 4, "Pequeno"));
        assertNull(service.criarTerreno(playerAt("Joao", world("world"), 10, 10), 101, "Grande"));
        assertEquals(1000000.0, balance, 0.001);
    }
}
