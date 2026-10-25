package io.github.joaovmundel.jocoTerrenos.economy;

/**
 * The money operations the lands need. Players are named by their (case-insensitive) player name, which is
 * all the Vault shipped with 1.5.2 understands.
 */
public interface EconomyGateway {

    /** @return whether an economy is currently registered; when {@code false} every other method fails safely */
    boolean isAvailable();

    boolean has(String playerName, double amount);

    /** @return whether the amount was taken */
    boolean withdraw(String playerName, double amount);

    /** @return whether the amount was given */
    boolean deposit(String playerName, double amount);

    /** @return the amount the way the server economy shows money; two decimals if there is no economy */
    String format(double amount);
}
