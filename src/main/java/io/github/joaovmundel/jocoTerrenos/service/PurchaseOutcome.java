package io.github.joaovmundel.jocoTerrenos.service;

import io.github.joaovmundel.jocoTerrenos.models.Terreno;

import java.math.BigDecimal;

/**
 * What happened when a player tried to buy a land that another player put up for sale.
 */
public final class PurchaseOutcome {

    public enum Status {
        SUCCESS,
        NOT_FOR_SALE,
        OWN_LAND,
        ECONOMY_UNAVAILABLE,
        INSUFFICIENT_FUNDS,
        /** The buyer already has lands called like this one, with every numbered variant taken too. */
        NAME_IN_USE,
        /** The payment could not be completed; nothing was charged. */
        PAYMENT_FAILED,
        /** The money moved but the land could not be saved; the payment was reversed. */
        SAVE_FAILED
    }

    private final Status status;
    private final BigDecimal price;
    private final Terreno terreno;
    private final String previousOwner;
    private final String previousName;

    private PurchaseOutcome(Status status, BigDecimal price, Terreno terreno, String previousOwner, String previousName) {
        this.status = status;
        this.price = price;
        this.terreno = terreno;
        this.previousOwner = previousOwner;
        this.previousName = previousName;
    }

    static PurchaseOutcome failure(Status status, BigDecimal price) {
        return new PurchaseOutcome(status, price, null, null, null);
    }

    static PurchaseOutcome success(Terreno terreno, BigDecimal price, String previousOwner, String previousName) {
        return new PurchaseOutcome(Status.SUCCESS, price, terreno, previousOwner, previousName);
    }

    public Status getStatus() {
        return status;
    }

    /** @return the asking price, or {@code null} when the land was not for sale */
    public BigDecimal getPrice() {
        return price;
    }

    /** @return the land with its new owner, only on {@link Status#SUCCESS} */
    public Terreno getTerreno() {
        return terreno;
    }

    /** @return the seller, only on {@link Status#SUCCESS} */
    public String getPreviousOwner() {
        return previousOwner;
    }

    /** Whether the land had to be renamed because the buyer already had one with that name. */
    public boolean wasRenamed() {
        return terreno != null && !terreno.getName().equalsIgnoreCase(previousName);
    }
}
