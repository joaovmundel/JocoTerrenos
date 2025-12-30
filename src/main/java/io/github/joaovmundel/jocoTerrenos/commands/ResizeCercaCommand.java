package io.github.joaovmundel.jocoTerrenos.commands;

import io.github.joaovmundel.jocoTerrenos.service.MessageService;
import io.github.joaovmundel.jocoTerrenos.utils.FenceUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ResizeCercaCommand implements CommandExecutor {

    private final MessageService messages;

    public ResizeCercaCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "only-player");
            return true;
        }

        if (args.length < 2) {
            messages.send(player, "resizecerca.usage");
            messages.send(player, "resizecerca.example1");
            messages.send(player, "resizecerca.example2");
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
            messages.send(player, "invalid-number");
            return true;
        }

        return true;
    }
}
