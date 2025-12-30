package io.github.joaovmundel.jocoTerrenos.commands;

import io.github.joaovmundel.jocoTerrenos.service.MessageService;
import io.github.joaovmundel.jocoTerrenos.utils.FenceUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RemoverCercaCommand implements CommandExecutor {

    private final MessageService messages;

    public RemoverCercaCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "only-player");
            return true;
        }

        if (args.length < 1) {
            messages.send(player, "removercerca.usage");
            messages.send(player, "removercerca.example1");
            messages.send(player, "removercerca.example2");
            return true;
        }

        try {
            int tamanho = Integer.parseInt(args[0]);
            Location centerLoc = player.getLocation();
            String resultado = FenceUtils.removerCercas(centerLoc, tamanho);
            player.sendMessage(resultado);
        } catch (NumberFormatException e) {
            messages.send(player, "invalid-number");
            return true;
        }

        return true;
    }
}
