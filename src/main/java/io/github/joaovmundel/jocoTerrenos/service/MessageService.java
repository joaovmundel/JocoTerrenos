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
 * the jar (for locale files saved by an older version).
 */
public class MessageService {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String DEFAULT_LANG = "pt_BR";
    private static final JocoLogging logger = new JocoLogging(MessageService.class.getName());

    private final JavaPlugin plugin;
    private YamlConfiguration active = new YamlConfiguration();
    private YamlConfiguration fallback = new YamlConfiguration();
    private YamlConfiguration bundled = new YamlConfiguration();

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initLocalesFolderAndDefaults() {
        File locales = new File(plugin.getDataFolder(), "locales");
        if (!locales.exists() && !locales.mkdirs()) {
            logger.warning("Could not create locales folder inside plugin data folder.");
        }
        saveResourceIfMissing("locales/pt_BR.yml");
        saveResourceIfMissing("locales/en_US.yml");
    }

    private void saveResourceIfMissing(String path) {
        File out = new File(plugin.getDataFolder(), path);
        if (!out.exists()) {
            plugin.saveResource(path, false);
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
            logger.warning("Não foi possível ler " + file.getName() + ": " + e.getMessage());
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
            logger.warning("Não foi possível ler os textos embutidos de " + code + ": " + e.getMessage());
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
            logger.warning("YAML inválido em " + name + ": " + e.getMessage());
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

    public String get(String key) {
        String value = active.getString(key);
        if (value == null) value = fallback.getString(key);
        if (value == null) value = bundled.getString(key);
        if (value == null) return key;
        return colorize(value);
    }

    public List<String> getList(String key) {
        List<String> list = active.getStringList(key);
        if (list.isEmpty()) {
            list = fallback.getStringList(key);
        }
        if (list.isEmpty()) {
            list = bundled.getStringList(key);
        }
        List<String> colored = new ArrayList<String>(list.size());
        for (String line : list) {
            colored.add(colorize(line));
        }
        return colored;
    }

    public String format(String key, Map<String, ?> placeholders) {
        String raw = get(key);
        return applyPlaceholders(raw, placeholders);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Collections.<String, Object>emptyMap());
    }

    public void send(CommandSender sender, String key, Map<String, ?> placeholders) {
        sender.sendMessage(applyPlaceholders(get(key), placeholders));
    }

    public void sendList(CommandSender sender, String key) {
        for (String line : getList(key)) {
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

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
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
