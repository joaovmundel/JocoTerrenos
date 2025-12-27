package io.github.joaovmundel.jocoTerrenos.commands;

import io.github.joaovmundel.jocoTerrenos.utils.FenceUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RemoverCercaCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando só pode ser executado por um jogador!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cUso: /removercerca <tamanho>");
            player.sendMessage("§7Exemplo: /removercerca 10");
            player.sendMessage("§7Isso removerá as cercas de uma área 10x10 ao seu redor");
            return true;
        }

        try {
            int tamanho = Integer.parseInt(args[0]);

            // Usa a localização atual do jogador como centro
            Location centerLoc = player.getLocation();

            // Chama a função de remover cercas
            String resultado = FenceUtils.removerCercas(centerLoc, tamanho);

            // Envia o resultado para o jogador
            player.sendMessage(resultado);

        } catch (NumberFormatException e) {
            player.sendMessage("§cPor favor, insira um número válido!");
            return true;
        }

        return true;
    }
}

