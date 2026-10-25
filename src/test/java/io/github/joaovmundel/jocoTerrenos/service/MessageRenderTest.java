package io.github.joaovmundel.jocoTerrenos.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class MessageRenderTest {

    private static final String SECTION = "§";

    @Test
    public void ampersandCodesBecomeColorCodes() {
        assertEquals(SECTION + "aOk " + SECTION + "f10" + SECTION + "7x" + SECTION + "f10", MessageService.render("&aOk &f10&7x&f10", ""));
    }

    @Test
    public void prefixPlaceholderIsReplacedByTheColoredPrefix() {
        String rendered = MessageService.render("{prefix}&cErro", "&8[&6Terrenos&8]&r ");

        assertEquals(SECTION + "8[" + SECTION + "6Terrenos" + SECTION + "8]" + SECTION + "r " + SECTION + "cErro", rendered);
    }

    @Test
    public void prefixPlaceholderDisappearsWhenThereIsNoPrefix() {
        assertEquals(SECTION + "cErro", MessageService.render("{prefix}&cErro", null));
    }

    @Test
    public void escapedNewlinesBecomeRealLineBreaks() {
        assertEquals(SECTION + "aOne\n" + SECTION + "7Two", MessageService.render("&aOne\\n&7Two", ""));
    }

    @Test
    public void otherPlaceholdersAreLeftForTheCaller() {
        assertEquals(SECTION + "7Dono: " + SECTION + "e{owner}", MessageService.render("&7Dono: &e{owner}", ""));
    }

    @Test
    public void everyRenderedTextIsFreeOfLiteralAmpersandCodes() {
        assertFalse(MessageService.render("&6a &lb &r&7c", "").contains("&"));
    }
}
