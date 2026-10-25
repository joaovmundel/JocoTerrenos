package io.github.joaovmundel.jocoTerrenos;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Checks the files shipped in the jar with the real Bukkit 1.5.2 classes, since the server itself cannot run
 * in the build: plugin.yml has to parse, locales have to be readable as UTF-8 and have the same keys, and
 * every message key the code asks for has to exist.
 */
public class ResourcesTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * Bukkit 1.5.2 builds {@link Permission} objects through {@code Bukkit.getServer()}, so parsing the
     * permissions of plugin.yml needs a server. A proxy that answers every call with a neutral value is
     * enough: nothing here depends on server behavior.
     */
    @BeforeClass
    public static void installFakeServer() {
        if (Bukkit.getServer() != null) {
            return;
        }
        final Logger logger = Logger.getLogger("ResourcesTest");
        Server server = (Server) Proxy.newProxyInstance(ResourcesTest.class.getClassLoader(), new Class<?>[]{Server.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if (method.getName().equals("getLogger")) {
                            return logger;
                        }
                        if (method.getName().equals("getPluginManager")) {
                            return Proxy.newProxyInstance(ResourcesTest.class.getClassLoader(),
                                    new Class<?>[]{PluginManager.class}, this);
                        }
                        return neutralValue(method.getReturnType());
                    }
                });
        Bukkit.setServer(server);
    }

    private static Object neutralValue(Class<?> type) {
        if (type == boolean.class) return Boolean.FALSE;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == float.class) return 0F;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        if (type == Set.class) return Collections.emptySet();
        if (type == List.class) return Collections.emptyList();
        if (type == Map.class) return Collections.emptyMap();
        return null;
    }
    /** A Java string literal, empty ones included: skipping {@code ""} would pair the quotes wrongly. */
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\\\r\\n]|\\\\.)*)\"");
    private static final Pattern MESSAGE_KEY = Pattern.compile(
            "^(terreno|protection|help|status|errors|cercar|removercerca|resizecerca|fences|action)[.][a-z0-9.\\-]+$"
                    + "|^(only-player|invalid-number|invalid-size|invalid-location|no-permission)$");

    private static String read(String resource) throws IOException {
        InputStream in = ResourcesTest.class.getResourceAsStream(resource);
        assertNotNull("missing resource " + resource, in);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), UTF_8);
        } finally {
            in.close();
        }
    }

    private static YamlConfiguration yaml(String resource) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(read(resource));
        return yaml;
    }

    @Test
    public void pluginYmlParsesWithTheBukkitParser() throws Exception {
        PluginDescriptionFile description = new PluginDescriptionFile(
                ResourcesTest.class.getResourceAsStream("/plugin.yml"));

        assertEquals("JocoTerrenos", description.getName());
        assertEquals("io.github.joaovmundel.jocoTerrenos.JocoTerrenos", description.getMain());
        assertEquals(1, description.getDepend().size());
        assertEquals("Vault", description.getDepend().get(0));
        assertEquals(4, description.getCommands().size());
        assertTrue(description.getCommands().containsKey("terreno"));
    }

    @Test
    public void permissionsHaveTheIntendedDefaults() throws Exception {
        PluginDescriptionFile description = new PluginDescriptionFile(
                ResourcesTest.class.getResourceAsStream("/plugin.yml"));
        Map<String, Permission> byName = new HashMap<String, Permission>();
        for (Permission permission : description.getPermissions()) {
            byName.put(permission.getName(), permission);
        }

        assertEquals(PermissionDefault.TRUE, byName.get("jocoterrenos.terreno").getDefault());
        for (String adminOnly : new String[]{"jocoterrenos.cercar", "jocoterrenos.resizecerca",
                "jocoterrenos.removercerca", "jocoterrenos.bypass", "jocoterrenos.admin"}) {
            assertNotNull(adminOnly + " is not declared", byName.get(adminOnly));
            assertEquals(adminOnly, PermissionDefault.OP, byName.get(adminOnly).getDefault());
        }
        Set<String> adminChildren = byName.get("jocoterrenos.admin").getChildren().keySet();
        assertTrue(adminChildren.contains("jocoterrenos.cercar"));
        assertTrue(adminChildren.contains("jocoterrenos.resizecerca"));
        assertTrue(adminChildren.contains("jocoterrenos.removercerca"));
        assertTrue(adminChildren.contains("jocoterrenos.bypass"));
    }

    @Test
    public void everyCommandPermissionIsDeclared() throws Exception {
        PluginDescriptionFile description = new PluginDescriptionFile(
                ResourcesTest.class.getResourceAsStream("/plugin.yml"));
        Set<String> declared = new TreeSet<String>();
        for (Permission permission : description.getPermissions()) {
            declared.add(permission.getName());
        }
        for (Map.Entry<String, Map<String, Object>> command : description.getCommands().entrySet()) {
            String permission = (String) command.getValue().get("permission");
            assertTrue(command.getKey() + " uses an undeclared permission " + permission, declared.contains(permission));
        }
    }

    @Test
    public void localesParseAndHaveTheHelpList() throws Exception {
        for (String locale : new String[]{"/locales/pt_BR.yml", "/locales/en_US.yml"}) {
            YamlConfiguration yaml = yaml(locale);

            assertTrue(locale, yaml.getString("only-player").length() > 0);
            assertTrue(locale, yaml.getStringList("help.lines").size() >= 9);
        }
    }

    @Test
    public void bothLocalesDefineTheSameKeys() throws Exception {
        Set<String> portuguese = leafKeys(yaml("/locales/pt_BR.yml"));
        Set<String> english = leafKeys(yaml("/locales/en_US.yml"));

        Set<String> onlyPortuguese = new TreeSet<String>(portuguese);
        onlyPortuguese.removeAll(english);
        Set<String> onlyEnglish = new TreeSet<String>(english);
        onlyEnglish.removeAll(portuguese);

        assertTrue("missing in en_US: " + onlyPortuguese, onlyPortuguese.isEmpty());
        assertTrue("missing in pt_BR: " + onlyEnglish, onlyEnglish.isEmpty());
    }

    @Test
    public void everyMessageKeyUsedByTheCodeExistsInBothLocales() throws Exception {
        Set<String> portuguese = leafKeys(yaml("/locales/pt_BR.yml"));
        Set<String> english = leafKeys(yaml("/locales/en_US.yml"));
        Set<String> used = messageKeysUsedInSources();
        used.removeAll(leafKeys(yaml("/config.yml"))); // "protection.message-cooldown-ms" is a setting, not a message

        assertTrue("the scan found no keys, the test is broken", used.size() > 40);
        Set<String> missingPt = new TreeSet<String>(used);
        missingPt.removeAll(portuguese);
        Set<String> missingEn = new TreeSet<String>(used);
        missingEn.removeAll(english);
        assertTrue("used in code but missing in pt_BR: " + missingPt, missingPt.isEmpty());
        assertTrue("used in code but missing in en_US: " + missingEn, missingEn.isEmpty());
    }

    @Test
    public void noLocaleKeyIsDeadText() throws Exception {
        Set<String> used = messageKeysUsedInSources();
        Set<String> dead = leafKeys(yaml("/locales/pt_BR.yml"));
        dead.removeAll(used);
        dead.remove("prefix");
        dead.remove("locale-version");

        assertTrue("texts nobody asks for (delete them or use them): " + dead, dead.isEmpty());
    }

    @Test
    public void bothLocalesCarryTheSameVersion() throws Exception {
        assertEquals(yaml("/locales/pt_BR.yml").getInt("locale-version"), yaml("/locales/en_US.yml").getInt("locale-version"));
        assertTrue(yaml("/locales/pt_BR.yml").getInt("locale-version") >= 1);
    }

    @Test
    public void defaultConfigIsConsistent() throws Exception {
        YamlConfiguration config = yaml("/config.yml");

        String type = config.getString("database.type");
        assertTrue(type, type.equals("SQLITE") || type.equals("MYSQL"));
        Material.valueOf(config.getString("lands.fence-material"));
        assertTrue(config.getInt("lands.min-size") <= config.getInt("lands.default-size"));
        assertTrue(config.getInt("lands.default-size") <= config.getInt("lands.max-size"));
        assertTrue(config.getLong("protection.message-cooldown-ms") > 0);
        assertEquals(java.util.Collections.singletonList("world"), config.getStringList("lands.allowed-worlds"));
        assertTrue(config.getDouble("lands.block-price") > 0);
    }

    private static Set<String> leafKeys(YamlConfiguration yaml) {
        Set<String> keys = new TreeSet<String>();
        for (String key : yaml.getKeys(true)) {
            if (!yaml.isConfigurationSection(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static Set<String> messageKeysUsedInSources() throws IOException {
        Set<String> keys = new TreeSet<String>();
        for (Path file : javaSources(new File("src/main/java").toPath())) {
            Matcher literal = STRING_LITERAL.matcher(new String(Files.readAllBytes(file), UTF_8));
            while (literal.find()) {
                if (MESSAGE_KEY.matcher(literal.group(1)).matches()) {
                    keys.add(literal.group(1));
                }
            }
        }
        return keys;
    }

    private static List<Path> javaSources(Path root) throws IOException {
        List<Path> files = new ArrayList<Path>();
        collect(root.toFile(), files);
        return files;
    }

    private static void collect(File directory, List<Path> files) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, files);
            } else if (child.getName().endsWith(".java")) {
                files.add(child.toPath());
            }
        }
    }
}
