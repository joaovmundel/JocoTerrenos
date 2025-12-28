package io.github.joaovmundel.jocoTerrenos.commands;

import io.github.joaovmundel.jocoTerrenos.JocoTerrenos;
import io.github.joaovmundel.jocoTerrenos.exceptions.TerrenoNotFoundException;
import io.github.joaovmundel.jocoTerrenos.infrastructure.JocoLogging;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.service.TerrenoService;
import io.github.joaovmundel.jocoTerrenos.utils.FenceUtils;
import io.github.joaovmundel.jocoTerrenos.utils.LocationUtils;
import io.github.joaovmundel.jocoTerrenos.utils.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("SameReturnValue")
public class TerrenoCommand implements CommandExecutor, TabCompleter {

    private final JocoLogging logger = new JocoLogging(this.getClass().getName());
    private final TerrenoService terrenoService;
    private static final List<String> SUB_COMMANDS = Arrays.asList("comprar", "preco", "listar", "info", "deletar", "pvp", "mobs", "publico", "tp");

    public TerrenoCommand(JocoTerrenos plugin) {
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
        return switch (subCommand) {
            case "comprar" -> handleComprar(player, args);
            case "listar" -> handleListar(player);
            case "info" -> handleInfo(player, args);
            case "deletar" -> handleDeletar(player, args);
            case "pvp" -> handleTogglePvp(player, args);
            case "mobs" -> handleToggleMobs(player, args);
            case "publico" -> handleTogglePublico(player, args);
            case "tp" -> handleTp(player, args);
            case "preco" -> handlePreco(player, args);
            default -> {
                sendHelp(player);
                yield true;
            }
        };
    }

    private boolean handleComprar(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUso: /terreno comprar <tamanho> <nome>");
            player.sendMessage("§7Exemplo: /terreno comprar 10 casa");
            return true;
        }

        try {
            int tamanho = Integer.parseInt(args[1]);
            String nome = joinArgs(args, 2);

            if (nome.length() > 15) {
                player.sendMessage("§cO nome do terreno não pode ter mais que 12 caracteres!");
                return true;
            }

            if (terrenoService.tamanhoValido(tamanho)) {
                player.sendMessage("§cTamanho mínimo: " + terrenoService.getTamanhoMinimo());
                player.sendMessage("§cTamanho máximo: " + terrenoService.getTamanhoMaximo());
                return true;
            }

            double custo = terrenoService.calcularCustoTerreno(tamanho);
            player.sendMessage("§7Preço do terreno: §e" + String.format("%.2f", custo));

            Optional<Terreno> created = terrenoService.criarTerreno(player, tamanho, nome);

            if (created.isPresent()) {
                FenceUtils.colocarCercas(player, tamanho);
                Terreno terreno = created.get();
                player.sendMessage("§aTerreno comprado com sucesso!");
                player.sendMessage("§7Dono: §f" + player.getName());
                player.sendMessage("§7Terreno: §f" + terreno.getName());
                player.sendMessage("§7Tamanho: " + tamanho + "x" + tamanho);
            } else {
                player.sendMessage("§cNão foi possível comprar o terreno. Verifique saldo, nome único e parâmetros.");
                return false;
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cPor favor, insira um número válido para tamanho!");
            return false;
        }

        return true;
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
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
                    "§7#%d §7- §6%s: §fTamanho: §e%dx%d §f- PvP: %s §f- Mobs: %s",
                    terreno.getId(),
                    StringUtils.capitalizeFirstLetter(terreno.getName()),
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
            try {
                // Info do terreno atual
                Terreno t = terrenoService.getCurrentTerreno(player);
                exibirInfo(player, t);
                return true;
            } catch (TerrenoNotFoundException ex) {
                player.sendMessage("§eVocê não está em nenhum terreno no momento.");
                logger.warning(ex.getMessage());
                return true;
            }
        }
        String nome = joinArgs(args, 1);
        String donoUUID = player.getUniqueId().toString();
        Optional<Terreno> terreno = terrenoService.buscarTerrenoPorNome(donoUUID, nome);
        if (terreno.isEmpty()) {
            player.sendMessage("§cTerreno não encontrado com esse nome.");
            return true;
        }
        exibirInfo(player, terreno.get());
        return true;
    }

    private void exibirInfo(Player player, Terreno t) {
        String donoNome = Bukkit.getOfflinePlayer(java.util.UUID.fromString(t.getDonoUUID())).getName();
        player.sendMessage("§a§l=== Informações do Terreno ===");
        player.sendMessage("§7Dono: §f" + (donoNome != null ? donoNome : t.getDonoUUID()));
        player.sendMessage("§7Terreno: §f" + t.getName());
        player.sendMessage("§7Tamanho: §f" + t.getSize() + "x" + t.getSize());
        player.sendMessage("§7Localização: §f" + LocationUtils.formattedLocation(t.getLocation()));
        player.sendMessage("§7PvP: " + (t.getPvp() ? "§aHabilitado" : "§cDesabilitado"));
        player.sendMessage("§7Mobs: " + (t.getMobs() ? "§aHabilitado" : "§cDesabilitado"));
        player.sendMessage("§7Acesso Público: " + (t.getPublicAccess() ? "§aSim" : "§cNão"));
        player.sendMessage("§7Membros: §f" + t.getMembers().size());
    }

    private boolean handleDeletar(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /terreno deletar <id>");
            return true;
        }

        try {
            Long id = Long.parseLong(args[1]);
            String playerUUID = player.getUniqueId().toString();
            Terreno terreno = terrenoService.buscarTerreno(id);

            if (!terrenoService.isDono(id, playerUUID)) {
                player.sendMessage("§cVocê não é o dono deste terreno!");
                return true;
            }

            // Remove as cercas antes de deletar
            Location loc = LocationUtils.parsearLocalizacao(terreno.getLocation());
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
        } catch (TerrenoNotFoundException e) {
            player.sendMessage("§cTerreno não encontrado!");
            logger.warning(e.getMessage());
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
            Terreno terreno = terrenoService.buscarTerreno(id);

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
                    Terreno t1 = terrenoService.buscarTerreno(id);
                    if (t1 == null) {
                        player.sendMessage("§cErro ao atualizar terreno!");
                        return true;
                    }
                    mensagem = "§aPvP " + (t1.getPvp() ? "habilitado" : "desabilitado") + "!";
                    break;
                case "mobs":
                    success = terrenoService.toggleMobs(id, playerUUID);
                    Terreno t2 = terrenoService.buscarTerreno(id);
                    if (t2 == null) {
                        player.sendMessage("§cErro ao atualizar terreno!");
                        return true;
                    }
                    mensagem = "§aMobs " + (t2.getMobs() ? "habilitados" : "desabilitados") + "!";
                    break;
                case "publico":
                    success = terrenoService.togglePublico(id, playerUUID);
                    Terreno t3 = terrenoService.buscarTerreno(id);
                    if (t3 == null) {
                        player.sendMessage("§cErro ao atualizar terreno!");
                        return true;
                    }
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
        } catch (TerrenoNotFoundException e) {
            player.sendMessage("§cErro ao atualizar terreno!");
            logger.warning(e.getMessage());
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

            Terreno terreno = terrenoService.buscarTerreno(id);

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
        } catch (TerrenoNotFoundException e) {
            player.sendMessage("§cTerreno não encontrado!");
            logger.warning("[JocoTerrenos] TerrenoCommand: " + e.getMessage());
        }
        return true;
    }

    private boolean handlePreco(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /terreno preco <tamanho>");
            player.sendMessage("§7Exemplo: /terreno preco 10");
            return true;
        }
        try {
            int tamanho = Integer.parseInt(args[1]);
            if (tamanho < terrenoService.getTamanhoMinimo()) {
                player.sendMessage("§cTamanho mínimo: " + terrenoService.getTamanhoMinimo());
                return true;
            }
            if (tamanho > terrenoService.getTamanhoMaximo()) {
                player.sendMessage("§cTamanho máximo: " + terrenoService.getTamanhoMaximo());
                return true;
            }
            double custo = terrenoService.calcularCustoTerreno(tamanho);
            player.sendMessage("§aPreço do terreno " + tamanho + "x" + tamanho + ": §e" + String.format("%.2f", custo));
        } catch (NumberFormatException e) {
            player.sendMessage("§cPor favor, insira um número válido para tamanho!");
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§a§l=== Comandos de Terreno ===");
        player.sendMessage("§7/terreno comprar <tamanho> <nome> §f- Compra um terreno");
        player.sendMessage("§7/terreno preco <tamanho> §f- Mostra o preço de um terreno NxN");
        player.sendMessage("§7/terreno listar §f- Lista seus terrenos");
        player.sendMessage("§7/terreno info [nome] §f- Informações do terreno atual ou pelo nome");
        player.sendMessage("§7/terreno deletar <id> §f- Deleta um terreno");
        player.sendMessage("§7/terreno pvp <id> §f- Alterna PvP");
        player.sendMessage("§7/terreno mobs <id> §f- Alterna Mobs");
        player.sendMessage("§7/terreno publico <id> §f- Alterna acesso público");
        player.sendMessage("§7/terreno tp <id> §f- Teleporta para um local seguro dentro do terreno");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (!(sender instanceof Player player)) {
            return suggestions;
        }

        String playerUUID = player.getUniqueId().toString();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            suggestions = SUB_COMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();
            switch (sub) {
                case "comprar":
                case "preco":
                    // Suggest common sizes
                    for (int sz : new int[]{5, 10, 15, 20, 25, 30, 50, 100}) {
                        String s = String.valueOf(sz);
                        if (s.startsWith(prefix)) suggestions.add(s);
                    }
                    break;
                case "info":
                    List<Terreno> terrenos = terrenoService.listarTerrenosDoJogador(playerUUID);
                    for (Terreno t : terrenos) {
                        String name = t.getName();
                        if (name != null && name.toLowerCase().startsWith(prefix)) suggestions.add(name);
                    }
                    break;
                case "deletar":
                case "pvp":
                case "mobs":
                case "publico":
                case "tp":
                    for (Terreno t : terrenoService.listarTerrenosDoJogador(playerUUID)) {
                        String idStr = String.valueOf(t.getId());
                        if (idStr.startsWith(prefix)) suggestions.add(idStr);
                    }
                    break;
            }
        } else if (args.length >= 3) {
            String sub = args[0].toLowerCase();
            if ("comprar".equals(sub)) {
                String typed = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).toLowerCase();
                for (Terreno t : terrenoService.listarTerrenosDoJogador(playerUUID)) {
                    String name = t.getName();
                    if (name != null && name.toLowerCase().startsWith(typed)) suggestions.add(name);
                }
            }
        }
        return suggestions;
    }
}
