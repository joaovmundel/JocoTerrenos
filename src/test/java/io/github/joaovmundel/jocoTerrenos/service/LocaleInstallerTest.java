package io.github.joaovmundel.jocoTerrenos.service;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocaleInstallerTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File locales;
    private String bundled;
    private LocaleInstaller installer;

    @Before
    public void setUp() {
        locales = new File(folder.getRoot(), "locales");
        bundled = "locale-version: 3\nprefix: new\n";
        Logger logger = Logger.getLogger("LocaleInstallerTest");
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
        installer = new LocaleInstaller(locales, new LocaleInstaller.ResourceSource() {
            @Override
            public InputStream open(String path) {
                if (!path.equals("locales/pt_BR.yml")) {
                    return null;
                }
                return new ByteArrayInputStream(bundled.getBytes(UTF_8));
            }
        }, logger);
    }

    private String read(String name) throws Exception {
        return new String(Files.readAllBytes(new File(locales, name).toPath()), UTF_8);
    }

    private void write(String name, String content) throws Exception {
        locales.mkdirs();
        Files.write(new File(locales, name).toPath(), content.getBytes(UTF_8));
    }

    @Test
    public void installsTheBundledFileWhenThereIsNone() throws Exception {
        installer.install("pt_BR");

        assertEquals(bundled, read("pt_BR.yml"));
    }

    @Test
    public void keepsAnEditedFileOfTheSameVersion() throws Exception {
        write("pt_BR.yml", "locale-version: 3\nprefix: customized by the admin\n");

        installer.install("pt_BR");

        assertEquals("locale-version: 3\nprefix: customized by the admin\n", read("pt_BR.yml"));
        assertEquals(1, locales.list().length);
    }

    @Test
    public void keepsAFileThatIsNewerThanTheJar() throws Exception {
        write("pt_BR.yml", "locale-version: 9\nprefix: from the future\n");

        installer.install("pt_BR");

        assertEquals("locale-version: 9\nprefix: from the future\n", read("pt_BR.yml"));
    }

    @Test
    public void replacesAnOlderFileAndKeepsABackup() throws Exception {
        write("pt_BR.yml", "locale-version: 1\nprefix: old\n");

        installer.install("pt_BR");

        assertEquals(bundled, read("pt_BR.yml"));
        assertEquals("locale-version: 1\nprefix: old\n", read("pt_BR.yml.v1.bak"));
    }

    @Test
    public void aFileWithoutAVersionCountsAsVersionZero() throws Exception {
        write("pt_BR.yml", "prefix: written before versions existed\n");

        installer.install("pt_BR");

        assertEquals(bundled, read("pt_BR.yml"));
        assertEquals("prefix: written before versions existed\n", read("pt_BR.yml.v0.bak"));
    }

    @Test
    public void neverOverwritesAnExistingBackup() throws Exception {
        write("pt_BR.yml", "locale-version: 1\nsecond\n");
        write("pt_BR.yml.v1.bak", "first backup\n");

        installer.install("pt_BR");

        assertEquals("first backup\n", read("pt_BR.yml.v1.bak"));
        assertEquals("locale-version: 1\nsecond\n", read("pt_BR.yml.v1-2.bak"));
        assertEquals(bundled, read("pt_BR.yml"));
    }

    @Test
    public void aMissingBundledResourceChangesNothing() throws Exception {
        write("en_US.yml", "locale-version: 1\nkept\n");

        installer.install("en_US");

        assertEquals("locale-version: 1\nkept\n", read("en_US.yml"));
        assertEquals(1, locales.list().length);
    }

    @Test
    public void versionIsReadFromItsOwnLineOnly() {
        assertEquals(7, LocaleInstaller.versionOf("# header\nlocale-version: 7\nx: y\n".getBytes(UTF_8)));
        assertEquals(0, LocaleInstaller.versionOf("x: 'locale-version: 7'\n".getBytes(UTF_8)));
        assertEquals(0, LocaleInstaller.versionOf("locale-version: abc\n".getBytes(UTF_8)));
        assertFalse(LocaleInstaller.versionOf("locale-version: 5\r\n".getBytes(UTF_8)) == 0);
        assertTrue(LocaleInstaller.versionOf("".getBytes(UTF_8)) == 0);
    }
}
