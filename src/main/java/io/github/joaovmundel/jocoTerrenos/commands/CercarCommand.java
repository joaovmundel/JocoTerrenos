package io.github.joaovmundel.jocoTerrenos.commands;

import io.github.joaovmundel.jocoTerrenos.utils.FenceUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CercarCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando só pode ser executado por um jogador!");
            return true;
        }
        
        if (args.length < 1) {
            player.sendMessage("§cUso: /cercar <área_em_m²>");
            player.sendMessage("§7Exemplo: /cercar 100");
            return true;
        }
        
        try {
            double areaM2 = Double.parseDouble(args[0]);
            FenceUtils.colocarCercas(player, areaM2);
        } catch (NumberFormatException e) {
            player.sendMessage("§cPor favor, insira um número válido!");
            return true;
        }
        
        return true;
    }
}

