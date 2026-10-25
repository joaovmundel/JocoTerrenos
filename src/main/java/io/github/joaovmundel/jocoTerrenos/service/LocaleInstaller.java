package io.github.joaovmundel.jocoTerrenos.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the locale files in the plugin folder up to date with the ones inside the jar.
 * <p>
 * Admins may edit those files, so they are never overwritten silently: every locale carries a
 * {@code locale-version: N} line, and only when the jar has a newer version is the file on disk renamed to a
 * backup before the new one is written.
 */
public final class LocaleInstaller {

    /** Where the bundled files come from; {@code JavaPlugin#getResource} behaves like this (null if missing). */
    public interface ResourceSource {
        InputStream open(String path);
    }

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Pattern VERSION_LINE = Pattern.compile("(?m)^locale-version:\\s*(\\d+)\\s*$");

    private final File localesFolder;
    private final ResourceSource source;
    private final Logger logger;

    public LocaleInstaller(File localesFolder, ResourceSource source, Logger logger) {
        this.localesFolder = localesFolder;
        this.source = source;
        this.logger = logger;
    }

    /** Makes sure {@code <code>.yml} exists in the folder and is not older than the bundled one. */
    public void install(String code) {
        String resource = "locales/" + code + ".yml";
        byte[] bundled = readBundled(resource);
        if (bundled == null) {
            logger.warning("Locale embutido ausente no jar: " + resource);
            return;
        }
        if (!localesFolder.exists() && !localesFolder.mkdirs()) {
            logger.warning("Nao foi possivel criar a pasta " + localesFolder.getPath());
            return;
        }

        File target = new File(localesFolder, code + ".yml");
        try {
            if (!target.exists()) {
                Files.write(target.toPath(), bundled);
                return;
            }
            int diskVersion = versionOf(Files.readAllBytes(target.toPath()));
            int bundledVersion = versionOf(bundled);
            if (diskVersion >= bundledVersion) {
                return;
            }
            File backup = backupName(code, diskVersion);
            Files.move(target.toPath(), backup.toPath());
            Files.write(target.toPath(), bundled);
            logger.info("Textos '" + code + "' atualizados da versao " + diskVersion + " para " + bundledVersion
                    + ". Copia do arquivo antigo: locales/" + backup.getName());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Nao foi possivel instalar o locale " + code + "; o arquivo atual continua em uso", e);
        }
    }

    /** @return the {@code locale-version} of the content, or 0 when it has none */
    static int versionOf(byte[] content) {
        Matcher matcher = VERSION_LINE.matcher(new String(content, UTF_8));
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private File backupName(String code, int version) {
        File backup = new File(localesFolder, code + ".yml.v" + version + ".bak");
        for (int attempt = 2; backup.exists(); attempt++) {
            backup = new File(localesFolder, code + ".yml.v" + version + "-" + attempt + ".bak");
        }
        return backup;
    }

    private byte[] readBundled(String resource) {
        InputStream in = source.open(resource);
        if (in == null) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Nao foi possivel ler " + resource + " do jar", e);
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                logger.log(Level.FINE, "Erro ao fechar " + resource + " (ignorado)", e);
            }
        }
    }
}
