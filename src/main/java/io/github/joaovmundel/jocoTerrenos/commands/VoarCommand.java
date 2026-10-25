package io.github.joaovmundel.jocoTerrenos.commands;

import io.github.joaovmundel.jocoTerrenos.service.FlightService;
import io.github.joaovmundel.jocoTerrenos.service.MessageService;
import io.github.joaovmundel.jocoTerrenos.service.TerrenoService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /voar}: switches flight on or off. It only shows the result; the rules are in {@link FlightService}.
 */
public class VoarCommand implements CommandExecutor {

    private final FlightService flight;
    private final TerrenoService terrenos;
    private final MessageService messages;

    public VoarCommand(FlightService flight, TerrenoService terrenos, MessageService messages) {
        this.flight = flight;
        this.terrenos = terrenos;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(FlightService.PERMISSION)) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player)) {
            messages.send(sender, "only-player");
            return true;
        }
        Player player = (Player) sender;

        switch (flight.toggle(player)) {
            case ENABLED:
                messages.send(player, "fly.enabled");
                break;
            case DISABLED:
                messages.send(player, "fly.disabled");
                break;
            case WORLD_NOT_ALLOWED:
                messages.send(player, "fly.world", MessageService.placeholders("worlds", terrenos.getMundosPermitidos().describe()));
                break;
            case NOT_IN_OWN_LAND:
                messages.send(player, "fly.not-in-own-land");
                break;
            case CREATIVE_MODE:
                messages.send(player, "fly.creative");
                break;
            default:
                messages.send(player, "no-permission");
                break;
        }
        return true;
    }
}
