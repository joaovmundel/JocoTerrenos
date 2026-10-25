package io.github.joaovmundel.jocoTerrenos.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault backed {@link EconomyGateway}. The economy is looked up on every call, so it does not matter whether
 * the economy plugin enabled before or after this one, and a reloaded economy is picked up.
 */
public final class VaultEconomyGateway implements EconomyGateway {

    @Override
    public boolean isAvailable() {
        return economy() != null;
    }

    @Override
    public boolean has(String playerName, double amount) {
        Economy economy = economy();
        return economy != null && economy.has(playerName, amount);
    }

    @Override
    public boolean withdraw(String playerName, double amount) {
        Economy economy = economy();
        return economy != null && economy.withdrawPlayer(playerName, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(String playerName, double amount) {
        Economy economy = economy();
        return economy != null && economy.depositPlayer(playerName, amount).transactionSuccess();
    }

    private static Economy economy() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }
}
