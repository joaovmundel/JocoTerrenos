package io.github.joaovmundel.jocoTerrenos.infrastructure;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JocoLoggingTest {

    private final List<String> lines = new ArrayList<String>();
    private Handler handler;
    private Logger root;

    @Before
    public void captureLogs() {
        // the class logs to the server logger, or to "JocoTerrenos" when there is no server; both reach the root
        root = Logger.getLogger("");
        handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                lines.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        root.addHandler(handler);
    }

    @After
    public void restore() {
        root.removeHandler(handler);
        JocoLogging.setDebugEnabled(false);
    }

    @Test
    public void infoIsASingleTaggedLine() {
        new JocoLogging("io.github.joaovmundel.jocoTerrenos.utils.FenceUtils").info("hello");

        assertEquals(1, lines.size());
        assertEquals("[JocoTerrenos] hello", lines.get(0));
    }

    @Test
    public void warningIsASingleTaggedLine() {
        new JocoLogging("io.github.joaovmundel.jocoTerrenos.utils.FenceUtils").warning("careful");

        assertEquals(1, lines.size());
        assertEquals("[JocoTerrenos] careful", lines.get(0));
    }

    @Test
    public void debugIsSilentByDefault() {
        new JocoLogging("io.github.joaovmundel.jocoTerrenos.utils.FenceUtils").debug("noise");

        assertTrue(lines.isEmpty());
    }

    @Test
    public void debugShowsTheClassNameWhenEnabled() {
        JocoLogging.setDebugEnabled(true);

        new JocoLogging("io.github.joaovmundel.jocoTerrenos.utils.FenceUtils").debug("noise");

        assertEquals(1, lines.size());
        assertEquals("[JocoTerrenos] [debug] FenceUtils: noise", lines.get(0));
    }
}
