package io.github.joaovmundel.jocoTerrenos.commands;

import io.github.joaovmundel.jocoTerrenos.service.MessageService;
import io.github.joaovmundel.jocoTerrenos.utils.FenceUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RemoverCercaCommand implements CommandExecutor {

    private static final String PERMISSION = "jocoterrenos.removercerca";

    private final MessageService messages;

    public RemoverCercaCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            messages.send(sender, "no-permission");
            return true;
        }

        if (!(sender instanceof Player)) {
            messages.send(sender, "only-player");
            return true;
        }
        Player player = (Player) sender;

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
