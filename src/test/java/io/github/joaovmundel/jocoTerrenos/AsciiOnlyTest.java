package io.github.joaovmundel.jocoTerrenos;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * Minecraft 1.5.2 and its Windows console do not cope with accents: Bukkit reads plugin.yml, the console prints
 * log lines and old YAML loaders read files in the platform charset, so anything non-ASCII shows up garbled.
 * Every text the plugin can show must therefore be plain ASCII (code comments are free to use accents).
 */
public class AsciiOnlyTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\\\r\\n]|\\\\.)*\"");
    private static final Pattern NON_ASCII = Pattern.compile("[^\\x00-\\x7F]");

    @Test
    public void resourcesAreAscii() throws IOException {
        List<String> offenders = new ArrayList<String>();
        for (File file : new File("src/main/resources").listFiles()) {
            collectResources(file, offenders);
        }
        assertTrue("non-ASCII characters in resources: " + offenders, offenders.isEmpty());
    }

    @Test
    public void stringLiteralsInCodeAreAscii() throws IOException {
        List<String> offenders = new ArrayList<String>();
        collectJava(new File("src/main/java"), offenders);
        assertTrue("non-ASCII characters in string literals: " + offenders, offenders.isEmpty());
    }

    private static void collectResources(File file, List<String> offenders) throws IOException {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                collectResources(child, offenders);
            }
            return;
        }
        String content = new String(Files.readAllBytes(file.toPath()), UTF_8);
        if (NON_ASCII.matcher(content).find()) {
            offenders.add(file.getName() + " " + describe(content));
        }
    }

    private static void collectJava(File file, List<String> offenders) throws IOException {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                collectJava(child, offenders);
            }
            return;
        }
        if (!file.getName().endsWith(".java")) {
            return;
        }
        Matcher literal = STRING_LITERAL.matcher(new String(Files.readAllBytes(file.toPath()), UTF_8));
        while (literal.find()) {
            if (NON_ASCII.matcher(literal.group()).find()) {
                offenders.add(file.getName() + " " + literal.group());
            }
        }
    }

    private static String describe(String content) {
        Matcher match = NON_ASCII.matcher(content);
        match.find();
        int from = Math.max(0, match.start() - 20);
        return "near '" + content.substring(from, Math.min(content.length(), match.end() + 20)).replace('\n', ' ') + "'";
    }
}
