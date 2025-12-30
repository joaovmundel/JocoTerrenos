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
import java.util.concurrent.CompletableFuture;

@SuppressWarnings({"SameReturnValue", "NullableProblems"})
public class TerrenoCommand implements CommandExecutor, TabCompleter {

    private final JocoTerrenos plugin;
    private final JocoLogging logger = new JocoLogging(this.getClass().getName());
    private final TerrenoService terrenoService;
    private static final List<String> SUB_COMMANDS = Arrays.asList("comprar", "preco", "listar", "info", "deletar", "pvp", "mobs", "publico", "tp");

    public TerrenoCommand(JocoTerrenos plugin) {
        this.terrenoService = plugin.getTerrenoService();
        this.plugin = plugin;
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
            player.sendMessage("");
            player.sendMessage("§cUso: /terreno comprar [tamanho] [nome]");
            player.sendMessage("§7Exemplo: /terreno comprar 10 casa");
            player.sendMessage("");
            return true;
        }

        try {
            int tamanho = Integer.parseInt(args[1]);
            String nome = joinArgs(args, 2);

            if (nome.length() > 15) {
                player.sendMessage("§cO nome do terreno não pode ter mais que 12 caracteres!");
                return true;
            }

            if (!terrenoService.tamanhoValido(tamanho)) {
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
            CompletableFuture<Terreno> asyncSearch = terrenoService.getCurrentTerrenoAsync(player);

            asyncSearch.thenAccept(terreno -> {
                Bukkit.getScheduler().runTask(plugin, () -> exibirInfo(player, terreno));
            }).exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§eVocê não está em nenhum terreno no momento.");
                });
                logger.warning(ex.getMessage());
                return null;
            });

            return true;
        }

        try {
            String nome = joinArgs(args, 1);
            String donoUUID = player.getUniqueId().toString();
            Terreno terreno = terrenoService.buscarTerrenoPorNome(donoUUID, nome);
            exibirInfo(player, terreno);
        } catch (TerrenoNotFoundException ex) {
            player.sendMessage("§eTerreno não encontrado.");
        }
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
            player.sendMessage("§cUso: /terreno deletar [nome]");
            return true;
        }

        try {
            String name = args[1];
            String playerUUID = player.getUniqueId().toString();
            Terreno terreno = terrenoService.buscarTerrenoPorNome(playerUUID, name);

            // Deleta o terreno
            if (terrenoService.deletarTerreno(name, playerUUID)) {
                Location loc = LocationUtils.parsearLocalizacao(terreno.getLocation());
                if (loc != null) {
                    FenceUtils.removerCercas(loc, terreno.getSize());
                }
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
            player.sendMessage("§cUso: /terreno pvp [nome]");
            return true;
        }

        return toggleSetting(player, args[1], "pvp");
    }

    private boolean handleToggleMobs(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /terreno mobs [nome]");
            return true;
        }

        return toggleSetting(player, args[1], "mobs");
    }

    private boolean handleTogglePublico(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /terreno publico [nome]");
            return true;
        }

        return toggleSetting(player, args[1], "publico");
    }

    private boolean toggleSetting(Player player, String nome, String setting) {
        try {
            String playerUUID = player.getUniqueId().toString();

            if (!terrenoService.isDono(nome, playerUUID)) {
                player.sendMessage("§cVocê não é o dono deste terreno!");
                return true;
            }

            boolean success = false;
            String mensagem = "";

            switch (setting) {
                case "pvp":
                    success = terrenoService.togglePvp(nome, playerUUID);
                    Terreno t1 = terrenoService.buscarTerrenoPorNome(playerUUID, nome);
                    if (t1 == null) {
                        player.sendMessage("§cOcorreu um erro ao atualizar o terreno!");
                        return true;
                    }
                    mensagem = "§6[Terreno] §7PvP " + (t1.getPvp() ? "§ahabilitado" : "§cdesabilitado") + "!";
                    break;
                case "mobs":
                    success = terrenoService.toggleMobs(nome, playerUUID);
                    Terreno t2 = terrenoService.buscarTerrenoPorNome(playerUUID, nome);
                    if (t2 == null) {
                        player.sendMessage("§cOcorreu um erro ao atualizar o terreno!");
                        return true;
                    }
                    mensagem = "§6[Terreno] §7Mobs " + (t2.getMobs() ? "§ahabilitados" : "§cdesabilitados") + "!";
                    break;
                case "publico":
                    success = terrenoService.togglePublico(nome, playerUUID);
                    Terreno t3 = terrenoService.buscarTerrenoPorNome(playerUUID, nome);
                    if (t3 == null) {
                        player.sendMessage("§cOcorreu um erro ao atualizar o terreno!");
                        return true;
                    }
                    mensagem = "§6[Terreno] §7Acesso público " + (t3.getPublicAccess() ? "§ahabilitado" : "§cdesabilitado") + "!";
                    break;
            }

            if (success) {
                player.sendMessage(mensagem);
                player.sendMessage("§6[Terreno] §7Configuração atualizada com sucesso para §f" + nome + "§7!");
            } else {
                player.sendMessage("§cOcorreu um erro ao atualizar o terreno!");
                logger.warning("Erro ao atualizar configuração do terreno para " + setting);
                logger.warning("Jogador: " + player.getName() + " - Terreno: " + nome);
                logger.warning("Sucesso: " + success);
            }

        } catch (TerrenoNotFoundException e) {
            player.sendMessage("§cErro ao atualizar terreno!");
            logger.warning(e.getMessage());
        }

        return true;
    }

    private boolean handleTp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /terreno tp [nome]");
            return true;
        }
        try {
            String name = args[1];
            String playerUUID = player.getUniqueId().toString();

            if (!(terrenoService.isDono(name, playerUUID))) {
                player.sendMessage("§cVocê não tem permissão para teleportar para este terreno!");
                return true;
            }

            Optional<Location> safeLocOpt = terrenoService.getSafeTeleportLocation(name, playerUUID);
            if (safeLocOpt.isPresent()) {
                Location safe = safeLocOpt.get();
                player.teleport(safe);
                player.sendMessage("§aTeleportado para um local seguro no terreno §f" + name + "§a!");
            } else {
                player.sendMessage("§cNão foi possível encontrar um local seguro para teleportar neste terreno.");
            }
        } catch (TerrenoNotFoundException e) {
            player.sendMessage("§cTerreno não encontrado!");
            logger.warning("[JocoTerrenos] TerrenoCommand: " + e.getMessage());
        }
        return true;
    }

    private boolean handlePreco(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /terreno preco [tamanho]");
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
        player.sendMessage("§7/terreno comprar [tamanho] [nome] §f- Compra um terreno");
        player.sendMessage("§7/terreno preco [tamanho] §f- Mostra o preço de um terreno NxN");
        player.sendMessage("§7/terreno listar §f- Lista seus terrenos");
        player.sendMessage("§7/terreno info [nome] §f- Informações do terreno atual ou pelo nome");
        player.sendMessage("§7/terreno deletar [nome] §f- Deleta um terreno");
        player.sendMessage("§7/terreno pvp [nome] §f- Alterna PvP");
        player.sendMessage("§7/terreno mobs [nome] §f- Alterna Mobs");
        player.sendMessage("§7/terreno publico [nome] §f- Alterna acesso público");
        player.sendMessage("§7/terreno tp [nome] §f- Teleporta para um local seguro dentro do terreno");
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
                        String terrenoName = t.getName();
                        if (terrenoName.startsWith(prefix)) suggestions.add(terrenoName);
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
