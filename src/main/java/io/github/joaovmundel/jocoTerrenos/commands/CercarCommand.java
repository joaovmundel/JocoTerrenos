package io.github.joaovmundel.jocoTerrenos.commands;

import io.github.joaovmundel.jocoTerrenos.service.MessageService;
import io.github.joaovmundel.jocoTerrenos.utils.FenceUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CercarCommand implements CommandExecutor {

    private static final String PERMISSION = "jocoterrenos.cercar";

    private final MessageService messages;

    public CercarCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            messages.send(sender, "no-permission");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "only-player");
            return true;
        }
        
        if (args.length < 1) {
            messages.send(player, "cercar.usage");
            return true;
        }
        
        try {
            double areaM2 = Double.parseDouble(args[0]);
            FenceUtils.colocarCercas(player, areaM2);
        } catch (NumberFormatException e) {
            messages.send(player, "invalid-number");
            return true;
        }
        
        return true;
    }
}
