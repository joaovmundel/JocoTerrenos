package io.github.joaovmundel.jocoTerrenos.service;

import io.github.joaovmundel.jocoTerrenos.database.Schema;
import io.github.joaovmundel.jocoTerrenos.database.SqliteConnectionProvider;
import io.github.joaovmundel.jocoTerrenos.economy.EconomyGateway;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.models.TerrenoRole;
import io.github.joaovmundel.jocoTerrenos.protection.ClaimIndex;
import io.github.joaovmundel.jocoTerrenos.repositories.TerrenoRepository;
import io.github.joaovmundel.jocoTerrenos.service.FlightService.ToggleResult;
import org.bukkit.GameMode;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The /voar rules end to end without a server: fake players that record what the plugin does to their flight,
 * real lands in a real SQLite file, and the real world and permission checks.
 */
public class FlightServiceTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private SqliteConnectionProvider provider;
    private TerrenoRepository repository;
    private ClaimIndex index;
    private YamlConfiguration config;
    private TerrenoService terrenos;
    private FlightService flight;
    private final World overworld = world("world");
    private final World nether = world("world_nether");

    /** A player that remembers what was done to it. */
    private static final class FakePlayer {
        final String name;
        final Set<String> permissions = new HashSet<String>();
        final List<String> messages = new ArrayList<String>();
        GameMode gameMode = GameMode.SURVIVAL;
        boolean allowFlight;
        boolean flying;
        boolean onGround = true;
        Location location;
        Player proxy;

        FakePlayer(String name) {
            this.name = name;
        }
    }

    @Before
    public void setUp() throws Exception {
        Logger logger = Logger.getLogger("FlightServiceTest");
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
        provider = new SqliteConnectionProvider(folder.newFile("terrenos.db"), logger);
        Schema.create(provider.get(), Schema.Dialect.SQLITE);
        repository = new TerrenoRepository(provider, logger);
        index = new ClaimIndex();
        config = new YamlConfiguration();
        terrenos = new TerrenoService(repository, config, index, new EconomyGateway() {
            public boolean isAvailable() {
                return true;
            }

            public boolean has(String playerName, double amount) {
                return true;
            }

            public boolean withdraw(String playerName, double amount) {
                return true;
            }

            public boolean deposit(String playerName, double amount) {
                return true;
            }

            public String format(double amount) {
                return String.valueOf(amount);
            }
        }, logger);
        ProtectionService protection = new ProtectionService(index, new MessageService(null), 0);
        flight = new FlightService(protection, terrenos, repository, new MessageService(null));
    }

    @After
    public void tearDown() {
        provider.close();
    }

    // ---------------------------------------------------------------- helpers

    private static World world(final String name) {
        return (World) Proxy.newProxyInstance(FlightServiceTest.class.getClassLoader(), new Class<?>[]{World.class},
                new InvocationHandler() {
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

    private FakePlayer player(String name, boolean withPermission, World world, double x, double z) {
        final FakePlayer fake = new FakePlayer(name);
        if (withPermission) {
            fake.permissions.add(FlightService.PERMISSION);
        }
        fake.location = new Location(world, x, 64, z);
        fake.proxy = (Player) Proxy.newProxyInstance(FlightServiceTest.class.getClassLoader(), new Class<?>[]{Player.class},
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String m = method.getName();
                        if (m.equals("getName")) return fake.name;
                        if (m.equals("hasPermission") && args[0] instanceof String) return fake.permissions.contains(args[0]);
                        if (m.equals("getGameMode")) return fake.gameMode;
                        if (m.equals("getAllowFlight")) return fake.allowFlight;
                        if (m.equals("setAllowFlight")) { fake.allowFlight = (Boolean) args[0]; return null; }
                        if (m.equals("isFlying")) return fake.flying;
                        if (m.equals("setFlying")) { fake.flying = (Boolean) args[0]; return null; }
                        if (m.equals("isOnGround")) return fake.onGround;
                        if (m.equals("getLocation")) return fake.location;
                        if (m.equals("sendMessage") && args[0] instanceof String) { fake.messages.add((String) args[0]); return null; }
                        if (m.equals("equals")) return proxy == args[0];
                        if (m.equals("hashCode")) return System.identityHashCode(proxy);
                        return null;
                    }
                });
        return fake;
    }

    /** A land of 11x11 columns around (x, z) in the given world. */
    private void land(String owner, String name, String world, double x, double z, String... members) throws Exception {
        Terreno terreno = new Terreno();
        terreno.setDonoNome(owner);
        terreno.setName(name);
        terreno.setLocation(world + ":" + x + ":64.00:" + z);
        terreno.setSize(10);
        terreno.setPvp(false);
        terreno.setMobs(true);
        terreno.setPublicAccess(true);
        Terreno created = repository.create(terreno);
        for (String member : members) {
            repository.addMember(created.getId(), member, TerrenoRole.MEMBER);
        }
        terrenos.reloadClaims();
    }

    // ---------------------------------------------------------------- turning it on

    @Test
    public void theOwnerWithPermissionStartsFlyingInTheirLand() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);

        assertEquals(ToggleResult.ENABLED, flight.toggle(joao.proxy));

        assertTrue(joao.allowFlight);
        assertTrue(flight.isActive(joao.proxy));
        assertTrue("remembered in case the server crashes", repository.loadFlyingPlayers().contains("joao"));
    }

    @Test
    public void aMemberWithPermissionFliesInTheLandTheyBelongTo() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5, "maria");
        FakePlayer maria = player("Maria", true, overworld, 102.5, 100.5);

        assertEquals(ToggleResult.ENABLED, flight.toggle(maria.proxy));
    }

    @Test
    public void aStrangerWithPermissionCannotFlyInSomeoneElsesLand() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer pedro = player("Pedro", true, overworld, 100.5, 100.5);

        assertEquals(ToggleResult.NOT_IN_OWN_LAND, flight.toggle(pedro.proxy));
        assertFalse(pedro.allowFlight);
    }

    @Test
    public void nobodyFliesOnUnclaimedGround() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 500.5, 500.5);

        assertEquals(ToggleResult.NOT_IN_OWN_LAND, flight.toggle(joao.proxy));
    }

    @Test
    public void withoutThePermissionTheOwnerCannotFly() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer joao = player("Joao", false, overworld, 100.5, 100.5);

        assertEquals(ToggleResult.NO_PERMISSION, flight.toggle(joao.proxy));
        assertFalse(joao.allowFlight);
    }

    @Test
    public void onlyInTheWorldsWhereLandsCanBeBought() throws Exception {
        config.set("lands.allowed-worlds", Arrays.asList("world"));
        land("joao", "Casa", "world", 100.5, 100.5);
        land("joao", "Ninho", "world_nether", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, nether, 100.5, 100.5);

        assertEquals("his land in the nether does not help: the world is not allowed", ToggleResult.WORLD_NOT_ALLOWED, flight.toggle(joao.proxy));
    }

    @Test
    public void creativePlayersAreLeftAlone() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);
        joao.gameMode = GameMode.CREATIVE;

        assertEquals(ToggleResult.CREATIVE_MODE, flight.toggle(joao.proxy));
        assertFalse(flight.isActive(joao.proxy));
    }

    @Test
    public void flyingInTheAirStartsFlyingAtOnce() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);
        joao.onGround = false;

        flight.toggle(joao.proxy);

        assertTrue(joao.flying);
    }

    // ---------------------------------------------------------------- turning it off

    @Test
    public void theCommandAgainSwitchesItOff() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);
        flight.toggle(joao.proxy);

        assertEquals(ToggleResult.DISABLED, flight.toggle(joao.proxy));

        assertFalse(joao.allowFlight);
        assertFalse(joao.flying);
        assertFalse(flight.isActive(joao.proxy));
        assertTrue(repository.loadFlyingPlayers().isEmpty());
    }

    @Test
    public void leavingTheLandSwitchesItOffAtOnce() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);
        flight.toggle(joao.proxy);

        flight.revalidate(joao.proxy, new Location(overworld, 140.5, 64, 100.5));

        assertFalse(joao.allowFlight);
        assertFalse(flight.isActive(joao.proxy));
        assertEquals(FlightService.MESSAGE_OFF_LEFT_LAND, joao.messages.get(0));
    }

    @Test
    public void movingInsideTheLandKeepsFlying() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);
        flight.toggle(joao.proxy);

        flight.revalidate(joao.proxy, new Location(overworld, 103.5, 70, 98.5));

        assertTrue(joao.allowFlight);
        assertTrue(joao.messages.isEmpty());
    }

    @Test
    public void walkingIntoAnotherLandOfTheirsKeepsFlying() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        land("joao", "Sitio", "world", 130.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);
        flight.toggle(joao.proxy);

        flight.revalidate(joao.proxy, new Location(overworld, 130.5, 64, 100.5));

        assertTrue(flight.isActive(joao.proxy));
    }

    @Test
    public void teleportingAwayToUnclaimedGroundSwitchesItOff() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);
        flight.toggle(joao.proxy);

        flight.revalidate(joao.proxy, new Location(overworld, 900.5, 64, 900.5));

        assertFalse(joao.allowFlight);
    }

    @Test
    public void goingToAnotherWorldSwitchesItOffWithTheWorldMessage() throws Exception {
        config.set("lands.allowed-worlds", Arrays.asList("world", "world_nether"));
        land("joao", "Casa", "world", 100.5, 100.5);
        land("joao", "Ninho", "world_nether", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);
        flight.toggle(joao.proxy);
        config.set("lands.allowed-worlds", Arrays.asList("world"));

        flight.revalidate(joao.proxy, new Location(nether, 100.5, 64, 100.5));

        assertFalse(flight.isActive(joao.proxy));
        assertEquals(FlightService.MESSAGE_OFF_WORLD, joao.messages.get(0));
    }

    @Test
    public void losingThePermissionSwitchesItOff() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);
        flight.toggle(joao.proxy);
        joao.permissions.clear();

        flight.revalidate(joao.proxy, joao.location);

        assertFalse(flight.isActive(joao.proxy));
        assertEquals(FlightService.MESSAGE_OFF_PERMISSION, joao.messages.get(0));
    }

    @Test
    public void aLandThatChangesHandsGroundsItsFormerOwner() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);
        flight.toggle(joao.proxy);
        Terreno casa = repository.findByNameKey(TerrenoRepository.nameKey("joao", "Casa"));
        casa.setDonoNome("maria");
        casa.setName("Casa");
        repository.transferOwnership(casa);
        terrenos.reloadClaims();

        flight.revalidate(joao.proxy, joao.location);

        assertFalse(flight.isActive(joao.proxy));
    }

    @Test
    public void revalidatingSomeoneWhoDoesNotFlyDoesNothing() throws Exception {
        FakePlayer joao = player("Joao", true, overworld, 900.5, 900.5);

        flight.revalidate(joao.proxy, joao.location);

        assertTrue(joao.messages.isEmpty());
        assertFalse(joao.allowFlight);
    }

    @Test
    public void switchingItOffTwiceIsHarmless() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);
        flight.toggle(joao.proxy);

        flight.disable(joao.proxy);
        flight.disable(joao.proxy);

        assertFalse(flight.isActive(joao.proxy));
    }

    @Test
    public void anotherPluginsFlightIsNeverTouched() throws Exception {
        FakePlayer essentialsUser = player("Joao", true, overworld, 900.5, 900.5);
        essentialsUser.allowFlight = true; // granted by someone else

        flight.revalidate(essentialsUser.proxy, essentialsUser.location);
        flight.disable(essentialsUser.proxy);

        assertTrue(essentialsUser.allowFlight);
    }

    // ---------------------------------------------------------------- falling, crashes, shutdown

    @Test
    public void cuttingFlightOffInTheAirForgivesOneFall() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);
        flight.toggle(joao.proxy);
        flight.disable(joao.proxy);

        assertTrue(flight.consumeFallProtection(joao.proxy));
        assertFalse("only the first fall", flight.consumeFallProtection(joao.proxy));
    }

    @Test
    public void withoutHavingFlownNoFallIsForgiven() {
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);

        assertFalse(flight.consumeFallProtection(joao.proxy));
    }

    @Test
    public void leavingTheServerForgetsTheGrace() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);
        flight.toggle(joao.proxy);
        flight.disable(joao.proxy);

        flight.forget(joao.proxy);

        assertFalse(flight.consumeFallProtection(joao.proxy));
    }

    @Test
    public void aCrashLeavesTheFlagBehindAndTheNextLoginClearsIt() throws Exception {
        repository.markFlying("joao"); // a previous run died while he was flying
        flight.loadStaleFlyers();
        FakePlayer joao = player("Joao", true, overworld, 900.5, 900.5);
        joao.allowFlight = true; // still saved in his player file
        joao.flying = true;

        flight.restoreAfterCrash(joao.proxy);

        assertFalse(joao.allowFlight);
        assertFalse(joao.flying);
        assertTrue(repository.loadFlyingPlayers().isEmpty());
    }

    @Test
    public void aLoginWithoutAStaleRecordKeepsWhateverFlightTheyHave() {
        flight.loadStaleFlyers();
        FakePlayer maria = player("Maria", false, overworld, 900.5, 900.5);
        maria.allowFlight = true; // e.g. Essentials

        flight.restoreAfterCrash(maria.proxy);

        assertTrue(maria.allowFlight);
    }

    @Test
    public void aCreativePlayerKeepsTheirFlightEvenIfRecordedAsStale() {
        repository.markFlying("joao");
        flight.loadStaleFlyers();
        FakePlayer joao = player("Joao", true, overworld, 900.5, 900.5);
        joao.gameMode = GameMode.CREATIVE;
        joao.allowFlight = true;

        flight.restoreAfterCrash(joao.proxy);

        assertTrue(joao.allowFlight);
    }

    @Test
    public void switchingOffClearsTheCrashRecord() throws Exception {
        land("joao", "Casa", "world", 100.5, 100.5);
        FakePlayer joao = player("Joao", true, overworld, 100.5, 100.5);
        flight.toggle(joao.proxy);
        assertEquals(1, repository.loadFlyingPlayers().size());

        // disableAll() finds players with Bukkit.getPlayerExact, which needs a server, so it is not covered here;
        // it calls disable() for each of them
        flight.disable(joao.proxy);

        assertTrue(repository.loadFlyingPlayers().isEmpty());
        assertFalse(joao.allowFlight);
    }
}
