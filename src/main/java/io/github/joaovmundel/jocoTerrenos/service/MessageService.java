package io.github.joaovmundel.jocoTerrenos.service;

import io.github.joaovmundel.jocoTerrenos.infrastructure.JocoLogging;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MessageService {
    private final JavaPlugin plugin;
    private YamlConfiguration active;
    private YamlConfiguration fallback;

    private static final String DEFAULT_LANG = "pt_BR";
    private static final JocoLogging logger = new JocoLogging(MessageService.class.getName());

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initLocalesFolderAndDefaults() {
        File dataFolder = plugin.getDataFolder();
        boolean createDataFolder = false, createLocalesFolder = false;
        if (!dataFolder.exists()) {
            createDataFolder = dataFolder.mkdirs();
        }
        File locales = new File(dataFolder, "locales");
        if (!locales.exists()) {
            createLocalesFolder = locales.mkdirs();
        }

        if (!createDataFolder || !createLocalesFolder) {
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
        this.active = loadLocale(lang);
        this.fallback = loadLocale(DEFAULT_LANG);
        if (this.fallback == null) {
            this.fallback = new YamlConfiguration();
        }
        if (this.active == null) {
            this.active = this.fallback;
        }
    }

    private YamlConfiguration loadLocale(String code) {
        try {
            File file = new File(plugin.getDataFolder(), "locales/" + code + ".yml");
            if (file.exists()) {
                return YamlConfiguration.loadConfiguration(file);
            }
            InputStream in = plugin.getResource("locales/" + code + ".yml");
            if (in != null) {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
                in.close();
                return yml;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public String get(String key) {
        String value = active.getString(key);
        if (value == null) value = fallback.getString(key);
        if (value == null) return key;
        return colorize(value);
    }

    public List<String> getList(String key) {
        List<String> list = active.getStringList(key);
        if (list.isEmpty()) {
            list = fallback.getStringList(key);
        }
        return list.stream().map(this::colorize).toList();
    }

    public String format(String key, Map<String, ?> placeholders) {
        String raw = get(key);
        return applyPlaceholders(raw, placeholders);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Collections.emptyMap());
    }

    public void send(CommandSender sender, String key, Map<String, ?> placeholders) {
        String raw = get(key);
        String msg = applyPlaceholders(raw, placeholders);
        if (isJsonMessage(msg) && sender instanceof Player p) {
            String json = stripJsonPrefix(msg);
            if (!trySendJson(p, json)) {
                logger.warning("Falha ao enviar JSON, enviando como texto: " + json);
                sender.sendMessage(msg);
            }
            return;
        }
        sender.sendMessage(msg);
    }

    public void sendList(CommandSender sender, String key) {
        for (String line : getList(key)) {
            sender.sendMessage(line);
        }
    }

    private boolean isJsonMessage(String msg) {
        String s = msg.trim();
        return s.startsWith("json:");
    }

    private String stripJsonPrefix(String msg) {
        return msg.trim().substring("json:".length()).trim();
    }

    private boolean trySendJson(Player player, String json) {
        try {
            BaseComponent[] components = ComponentSerializer.parse(json);
            player.spigot().sendMessage(components);
            return true;
        } catch (Throwable t) {
            logger.warning("Erro ao parsear/enviar JSON: " + t.getMessage());
            return false;
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
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }
}
