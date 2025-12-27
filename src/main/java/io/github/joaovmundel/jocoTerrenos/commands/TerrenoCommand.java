package io.github.joaovmundel.jocoTerrenos.commands;

import io.github.joaovmundel.jocoTerrenos.JocoTerrenos;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.service.TerrenoService;
import io.github.joaovmundel.jocoTerrenos.utils.FenceUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class TerrenoCommand implements CommandExecutor {

    private final JocoTerrenos plugin;
    private final TerrenoService terrenoService;

    public TerrenoCommand(JocoTerrenos plugin) {
        this.plugin = plugin;
        this.terrenoService = plugin.getTerrenoService();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando só pode ser executado por um jogador!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "criar":
                return handleCriar(player, args);
            case "listar":
                return handleListar(player);
            case "info":
                return handleInfo(player, args);
            case "deletar":
                return handleDeletar(player, args);
            case "pvp":
                return handleTogglePvp(player, args);
            case "mobs":
                return handleToggleMobs(player, args);
            case "publico":
                return handleTogglePublico(player, args);
            case "tp":
                return handleTp(player, args);
            default:
                sendHelp(player);
                return true;
        }
    }

    private boolean handleCriar(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /terreno criar <tamanho>");
            player.sendMessage("§7Exemplo: /terreno criar 10");
            return true;
        }

        try {
            int tamanho = Integer.parseInt(args[1]);

            // Valida tamanho usando o service
            if (tamanho < terrenoService.getTamanhoMinimo()) {
                player.sendMessage("§cTamanho mínimo: " + terrenoService.getTamanhoMinimo());
                return true;
            }

            if (tamanho > terrenoService.getTamanhoMaximo()) {
                player.sendMessage("§cTamanho máximo: " + terrenoService.getTamanhoMaximo());
                return true;
            }

            // Cria o terreno usando o service
            Optional<Terreno> created = terrenoService.criarTerreno(player, tamanho);

            if (created.isPresent()) {
                // Coloca as cercas
                FenceUtils.colocarCercas(player, tamanho);

                Terreno terreno = created.get();
                player.sendMessage("§aTereno criado com sucesso!");
                player.sendMessage("§7ID: " + terreno.getId());
                player.sendMessage("§7Tamanho: " + tamanho + "x" + tamanho);
                player.sendMessage("§7Localização: " + terreno.getLocation());
            } else {
                player.sendMessage("§cErro ao criar terreno!");
            }

        } catch (NumberFormatException e) {
            player.sendMessage("§cPor favor, insira um número válido!");
        }

        return true;
    }

    private boolean handleListar(Player player) {
        String playerUUID = player.getUniqueId().toString();
        List<Terreno> terrenos = terrenoService.listarTerrenosDoJogador(playerUUID);

        if (terrenos.isEmpty()) {
            player.sendMessage("§eVocê não possui terrenos.");
            return true;
        }

        player.sendMessage("§a§l=== Seus Terrenos ===");
        for (Terreno terreno : terrenos) {
            player.sendMessage(String.format(
                    "§7#%d §f- Tamanho: §e%dx%d §f- PvP: %s §f- Mobs: %s",
                    terreno.getId(),
                    terreno.getSize(),
                    terreno.getSize(),
                    terreno.getPvp() ? "§aON" : "§cOFF",
                    terreno.getMobs() ? "§aON" : "§cOFF"
            ));
        }

        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /terreno info <id>");
            return true;
        }

        try {
            Long id = Long.parseLong(args[1]);
            Optional<Terreno> terreno = terrenoService.buscarTerreno(id);

            if (terreno.isEmpty()) {
                player.sendMessage("§cTerreno não encontrado!");
                return true;
            }

            Terreno t = terreno.get();
            player.sendMessage("§a§l=== Informações do Terreno #" + t.getId() + " ===");
            player.sendMessage("§7Tamanho: §f" + t.getSize() + "x" + t.getSize());
            player.sendMessage("§7Localização: §f" + t.getLocation());
            player.sendMessage("§7PvP: " + (t.getPvp() ? "§aHabilitado" : "§cDesabilitado"));
            player.sendMessage("§7Mobs: " + (t.getMobs() ? "§aHabilitado" : "§cDesabilitado"));
            player.sendMessage("§7Acesso Público: " + (t.getPublicAccess() ? "§aSim" : "§cNão"));
            player.sendMessage("§7Membros: §f" + t.getMembers().size());

        } catch (NumberFormatException e) {
            player.sendMessage("§cPor favor, insira um ID válido!");
        }

        return true;
    }

    private boolean handleDeletar(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /terreno deletar <id>");
            return true;
        }

        try {
            Long id = Long.parseLong(args[1]);
            String playerUUID = player.getUniqueId().toString();
            Optional<Terreno> terrenoOpt = terrenoService.buscarTerreno(id);

            // Verifica se o terreno existe
            if (terrenoOpt.isEmpty()) {
                player.sendMessage("§cTerreno não encontrado!");
                return true;
            }

            Terreno terreno = terrenoOpt.get();

            if (!terrenoService.isDono(id, playerUUID)) {
                player.sendMessage("§cVocê não é o dono deste terreno!");
                return true;
            }

            // Remove as cercas antes de deletar
            Location loc = terrenoService.parsearLocalizacao(terreno.getLocation());
            if (loc != null) {
                String resultado = FenceUtils.removerCercas(loc, terreno.getSize());
                player.sendMessage(resultado);
            }

            // Deleta o terreno
            if (terrenoService.deletarTerreno(id, playerUUID)) {
                player.sendMessage("§aTereno deletado com sucesso!");
            } else {
                player.sendMessage("§cErro ao deletar terreno!");
            }

        } catch (NumberFormatException e) {
            player.sendMessage("§cPor favor, insira um ID válido!");
        }

        return true;
    }

    private boolean handleTogglePvp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /terreno pvp <id>");
            return true;
        }

        return toggleSetting(player, args[1], "pvp");
    }

    private boolean handleToggleMobs(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /terreno mobs <id>");
            return true;
        }

        return toggleSetting(player, args[1], "mobs");
    }

    private boolean handleTogglePublico(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /terreno publico <id>");
            return true;
        }

        return toggleSetting(player, args[1], "publico");
    }

    private boolean toggleSetting(Player player, String idStr, String setting) {
        try {
            Long id = Long.parseLong(idStr);
            String playerUUID = player.getUniqueId().toString();

            // Verifica se o terreno existe
            Optional<Terreno> terrenoOpt = terrenoService.buscarTerreno(id);
            if (terrenoOpt.isEmpty()) {
                player.sendMessage("§cTerreno não encontrado!");
                return true;
            }

            // Verifica se é o dono
            if (!terrenoService.isDono(id, playerUUID)) {
                player.sendMessage("§cVocê não é o dono deste terreno!");
                return true;
            }

            boolean success = false;
            String mensagem = "";

            switch (setting) {
                case "pvp":
                    success = terrenoService.togglePvp(id, playerUUID);
                    Terreno t1 = terrenoService.buscarTerreno(id).get();
                    mensagem = "§aPvP " + (t1.getPvp() ? "habilitado" : "desabilitado") + "!";
                    break;
                case "mobs":
                    success = terrenoService.toggleMobs(id, playerUUID);
                    Terreno t2 = terrenoService.buscarTerreno(id).get();
                    mensagem = "§aMobs " + (t2.getMobs() ? "habilitados" : "desabilitados") + "!";
                    break;
                case "publico":
                    success = terrenoService.togglePublico(id, playerUUID);
                    Terreno t3 = terrenoService.buscarTerreno(id).get();
                    mensagem = "§aAcesso público " + (t3.getPublicAccess() ? "habilitado" : "desabilitado") + "!";
                    break;
            }

            if (success) {
                player.sendMessage(mensagem);
                player.sendMessage("§aTereno atualizado!");
            } else {
                player.sendMessage("§cErro ao atualizar terreno!");
            }

        } catch (NumberFormatException e) {
            player.sendMessage("§cPor favor, insira um ID válido!");
        }

        return true;
    }

    private boolean handleTp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /terreno tp <id>");
            return true;
        }
        try {
            Long id = Long.parseLong(args[1]);
            String playerUUID = player.getUniqueId().toString();

            Optional<Terreno> terrenoOpt = terrenoService.buscarTerreno(id);
            if (terrenoOpt.isEmpty()) {
                player.sendMessage("§cTerreno não encontrado!");
                return true;
            }
            Terreno terreno = terrenoOpt.get();

            // Permissão: apenas dono ou admin
            if (!(terrenoService.isDono(id, playerUUID) || terrenoService.isAdminDoTerreno(id, playerUUID))) {
                player.sendMessage("§cVocê não tem permissão para teleportar para este terreno!");
                return true;
            }

            Optional<Location> safeLocOpt = terrenoService.getSafeTeleportLocation(id, playerUUID);
            if (safeLocOpt.isPresent()) {
                Location safe = safeLocOpt.get();
                player.teleport(safe);
                player.sendMessage("§aTeleportado para um local seguro no terreno #" + id + "!");
            } else {
                player.sendMessage("§cNão foi possível encontrar um local seguro para teleportar neste terreno.");
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cPor favor, insira um ID válido!");
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§a§l=== Comandos de Terreno ===");
        player.sendMessage("§7/terreno criar <tamanho> §f- Cria um terreno");
        player.sendMessage("§7/terreno listar §f- Lista seus terrenos");
        player.sendMessage("§7/terreno info <id> §f- Informações do terreno");
        player.sendMessage("§7/terreno deletar <id> §f- Deleta um terreno");
        player.sendMessage("§7/terreno pvp <id> §f- Alterna PvP");
        player.sendMessage("§7/terreno mobs <id> §f- Alterna Mobs");
        player.sendMessage("§7/terreno publico <id> §f- Alterna acesso público");
        player.sendMessage("§7/terreno tp <id> §f- Teleporta para um local seguro dentro do terreno");
    }
}

