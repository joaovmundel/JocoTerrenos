package io.github.joaovmundel.jocoTerrenos.listener;

import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Bukkit 1.5.2 refuses to register a listener at startup when an {@code @EventHandler} takes an event that has
 * no {@code getHandlerList()} (for example the abstract {@code PlayerBucketEvent}), which would disable the whole
 * plugin. This repeats the check the server makes so the mistake is caught at build time.
 */
public class ListenerRegistrationTest {

    private static final Class<?>[] LISTENERS = {
            BuildProtectionListener.class, EntityProtectionListener.class, EnvironmentProtectionListener.class,
            EntryProtectionListener.class, FlightListener.class};

    @Test
    public void everyHandlerTakesOneRegistrableEvent() {
        int handlers = 0;
        for (Class<?> listener : LISTENERS) {
            for (Method method : listener.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(EventHandler.class)) {
                    continue;
                }
                handlers++;
                String where = listener.getSimpleName() + "#" + method.getName();
                assertTrue(where + " must be public", Modifier.isPublic(method.getModifiers()));
                assertEquals(where + " must take exactly one parameter", 1, method.getParameterTypes().length);
                Class<?> event = method.getParameterTypes()[0];
                assertTrue(where + " must take an Event", Event.class.isAssignableFrom(event));
                assertTrue(where + ": " + event.getSimpleName() + " has no getHandlerList()", hasHandlerList(event));
            }
        }
        assertTrue("no handlers found, the test is broken", handlers >= 27);
    }

    /** Same walk as JavaPluginLoader#getRegistrationClass in CraftBukkit 1.5.2. */
    private static boolean hasHandlerList(Class<?> event) {
        Class<?> current = event;
        while (current != null && current != Event.class && Event.class.isAssignableFrom(current)) {
            try {
                current.getDeclaredMethod("getHandlerList");
                return true;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return false;
    }

    @Test
    public void theCheckRejectsAnAbstractEventWithoutAHandlerList() throws Exception {
        // PlayerBucketEvent is the classic mistake; make sure the helper really can fail
        Class<?> bucket = Class.forName("org.bukkit.event.player.PlayerBucketEvent");
        if (hasHandlerList(bucket)) {
            fail("expected PlayerBucketEvent to have no handler list in the 1.5.2 API");
        }
    }
}
