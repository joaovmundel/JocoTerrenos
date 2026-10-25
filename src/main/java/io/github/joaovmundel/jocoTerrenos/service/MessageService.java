package io.github.joaovmundel.jocoTerrenos.service;

import io.github.joaovmundel.jocoTerrenos.infrastructure.JocoLogging;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Player messages from {@code locales/<language>.yml}. Bukkit 1.5.2 reads YAML in the platform charset, so the
 * files are decoded as UTF-8 here. Missing keys fall back to the default language, then to the copy bundled in
 * the jar.
 * <p>
 * Texts use {@code &} color codes, the {@code {prefix}} placeholder (replaced by the {@code prefix} key) and
 * {@code \n} for line breaks.
 */
public class MessageService {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String DEFAULT_LANG = "pt_BR";
    private static final String[] BUNDLED_LOCALES = {"pt_BR", "en_US"};
    private static final String PREFIX_KEY = "prefix";
    private static final String PREFIX_PLACEHOLDER = "{prefix}";
    private static final JocoLogging logger = new JocoLogging(MessageService.class.getName());

    private final JavaPlugin plugin;
    private YamlConfiguration active = new YamlConfiguration();
    private YamlConfiguration fallback = new YamlConfiguration();
    private YamlConfiguration bundled = new YamlConfiguration();

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Creates the locale files on first run and replaces the ones from an older plugin version (with a backup). */
    public void initLocalesFolderAndDefaults() {
        LocaleInstaller installer = new LocaleInstaller(new File(plugin.getDataFolder(), "locales"),
                new LocaleInstaller.ResourceSource() {
                    @Override
                    public InputStream open(String path) {
                        return plugin.getResource(path);
                    }
                }, plugin.getLogger());
        for (String code : BUNDLED_LOCALES) {
            installer.install(code);
        }
    }

    public void reload() {
        String lang = plugin.getConfig().getString("language", DEFAULT_LANG);
        this.bundled = orEmpty(loadBundledLocale(DEFAULT_LANG));
        this.fallback = orEmpty(loadLocaleFile(DEFAULT_LANG));
        YamlConfiguration selected = loadLocaleFile(lang);
        this.active = selected != null ? selected : this.fallback;
    }

    private static YamlConfiguration orEmpty(YamlConfiguration yaml) {
        return yaml != null ? yaml : new YamlConfiguration();
    }

    private YamlConfiguration loadLocaleFile(String code) {
        File file = new File(plugin.getDataFolder(), "locales/" + code + ".yml");
        if (!file.exists()) {
            return loadBundledLocale(code);
        }
        try {
            return parse(Files.readAllBytes(file.toPath()), file.getName());
        } catch (IOException e) {
            logger.warning("Nao foi possivel ler " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private YamlConfiguration loadBundledLocale(String code) {
        InputStream in = plugin.getResource("locales/" + code + ".yml");
        if (in == null) {
            return null;
        }
        try {
            return parse(readAll(in), code + ".yml (embutido)");
        } catch (IOException e) {
            logger.warning("Nao foi possivel ler os textos embutidos de " + code + ": " + e.getMessage());
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    private static YamlConfiguration parse(byte[] content, String name) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(new String(content, UTF_8));
        } catch (InvalidConfigurationException e) {
            logger.warning("YAML invalido em " + name + ": " + e.getMessage());
            return null;
        }
        return yaml;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException e) {
            logger.warning("Erro ao fechar um recurso embutido (ignorado): " + e.getMessage());
        }
    }

    private String rawString(String key) {
        String value = active.getString(key);
        if (value == null) value = fallback.getString(key);
        if (value == null) value = bundled.getString(key);
        return value;
    }

    private List<String> rawList(String key) {
        List<String> list = active.getStringList(key);
        if (list.isEmpty()) {
            list = fallback.getStringList(key);
        }
        if (list.isEmpty()) {
            list = bundled.getStringList(key);
        }
        return list;
    }

    /** @return the colored text of the key, or the key itself when no locale defines it */
    public String get(String key) {
        String value = rawString(key);
        if (value == null) return key;
        return render(value);
    }

    public List<String> getList(String key) {
        List<String> rendered = new ArrayList<String>();
        for (String line : rawList(key)) {
            rendered.add(render(line));
        }
        return rendered;
    }

    private String render(String raw) {
        return render(raw, rawString(PREFIX_KEY));
    }

    /** Expands {prefix} and line breaks, then the color codes. */
    static String render(String raw, String prefix) {
        String text = raw.replace(PREFIX_PLACEHOLDER, prefix != null ? prefix : "").replace("\\n", "\n");
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public String format(String key, Map<String, ?> placeholders) {
        String raw = get(key);
        return applyPlaceholders(raw, placeholders);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Collections.<String, Object>emptyMap());
    }

    public void send(CommandSender sender, String key, Map<String, ?> placeholders) {
        sendText(sender, applyPlaceholders(get(key), placeholders));
    }

    public void sendList(CommandSender sender, String key) {
        for (String line : getList(key)) {
            sender.sendMessage(line);
        }
    }

    /** Sends already rendered text, one chat line per line break (the 1.5.2 chat cannot show a newline). */
    public void sendText(CommandSender sender, String text) {
        for (String line : text.split("\n")) {
            sender.sendMessage(line);
        }
    }

    private String applyPlaceholders(String input, Map<String, ?> placeholders) {
        if (input == null || placeholders == null || placeholders.isEmpty()) return input;
        String out = input;
        for (Map.Entry<String, ?> e : placeholders.entrySet()) {
            String k = Objects.toString(e.getKey());
            String v = Objects.toString(e.getValue());
            out = out.replace("{" + k + "}", v);
        }
        return out;
    }

    public static Map<String, Object> placeholders(Object... kv) {
        if (kv == null || kv.length == 0) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<String, Object>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }
}
