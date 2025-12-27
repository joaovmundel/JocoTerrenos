package io.github.joaovmundel.jocoTerrenos.commands;

import io.github.joaovmundel.jocoTerrenos.utils.FenceUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ResizeCercaCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando só pode ser executado por um jogador!");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§cUso: /resizecerca <tamanho_antigo> <tamanho_novo>");
            player.sendMessage("§7Exemplo: /resizecerca 10 20");
            player.sendMessage("§7Isso redimensionará uma área de 10x10 para 20x20");
            return true;
        }

        try {
            int tamanhoAntigo = Integer.parseInt(args[0]);
            int tamanhoNovo = Integer.parseInt(args[1]);

            // Usa a localização atual do jogador como centro
            Location centerLoc = player.getLocation();

            // Chama a função de resize
            String resultado = FenceUtils.resizeCercas(centerLoc, tamanhoAntigo, tamanhoNovo);

            // Envia o resultado para o jogador
            player.sendMessage(resultado);

        } catch (NumberFormatException e) {
            player.sendMessage("§cPor favor, insira números válidos!");
            return true;
        }

        return true;
    }
}

