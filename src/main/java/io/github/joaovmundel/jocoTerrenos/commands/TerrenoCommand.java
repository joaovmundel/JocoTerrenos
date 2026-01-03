package io.github.joaovmundel.jocoTerrenos.commands;

import io.github.joaovmundel.jocoTerrenos.JocoTerrenos;
import io.github.joaovmundel.jocoTerrenos.exceptions.TerrenoNotFoundException;
import io.github.joaovmundel.jocoTerrenos.infrastructure.JocoLogging;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.service.MessageService;
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
            plugin.getMessageService().send(sender, "only-player");
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
            String usageTitle = plugin.getMessageService().get("terreno.comprar.usage-title");
            if (usageTitle != null && !usageTitle.isEmpty()) player.sendMessage(usageTitle);
            plugin.getMessageService().send(player, "terreno.comprar.usage1");
            plugin.getMessageService().send(player, "terreno.comprar.usage2");
            if (usageTitle != null && !usageTitle.isEmpty()) player.sendMessage(usageTitle);
            return true;
        }

        try {
            int tamanho = Integer.parseInt(args[1]);
            String nome = joinArgs(args, 2);

            if (nome.length() > 15) {
                plugin.getMessageService().send(player, "terreno.comprar.nome-longo");
                return true;
            }

            if (!terrenoService.tamanhoValido(tamanho)) {
                plugin.getMessageService().send(player, "terreno.comprar.tamanho-invalido-min", MessageService.placeholders("min", terrenoService.getTamanhoMinimo()));
                plugin.getMessageService().send(player, "terreno.comprar.tamanho-invalido-max", MessageService.placeholders("max", terrenoService.getTamanhoMaximo()));
                return true;
            }

            // Verificação assíncrona de disponibilidade com espaçamento, snapshot da localização na main thread dentro do service
            int buffer = terrenoService.getEspacoEntreTerrenos();
            CompletableFuture<Boolean> dispFut = terrenoService.isAreaDisponivelAsync(player.getLocation(), tamanho, buffer);
            dispFut.handle((disponivel, ex) -> {
                if (ex != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getMessageService().send(player, "terreno.comprar.falha"));
                    logger.warning("Erro ao verificar disponibilidade: " + ex.getMessage());
                    return null;
                }
                if (Boolean.FALSE.equals(disponivel)) {
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getMessageService().send(player, "terreno.comprar.area-indisponivel", MessageService.placeholders("buffer", buffer)));
                    return null;
                }

                // Área disponível: prossegue criação na main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    double custo = terrenoService.calcularCustoTerreno(tamanho);
                    plugin.getMessageService().send(player, "terreno.comprar.preco", MessageService.placeholders("price", String.format("%.2f", custo)));

                    Optional<Terreno> created = terrenoService.criarTerreno(player, tamanho, nome);

                    if (created.isPresent()) {
                        FenceUtils.colocarCercas(player, tamanho);
                        Terreno terreno = created.get();
                        plugin.getMessageService().send(player, "terreno.comprar.sucesso");
                        plugin.getMessageService().send(player, "terreno.comprar.info-dono", MessageService.placeholders("player", player.getName()));
                        plugin.getMessageService().send(player, "terreno.comprar.info-terreno", MessageService.placeholders("name", terreno.getName()));
                        plugin.getMessageService().send(player, "terreno.comprar.info-tamanho", MessageService.placeholders("size", tamanho));
                    } else {
                        plugin.getMessageService().send(player, "terreno.comprar.falha");
                    }
                });
                return null;
            });

        } catch (NumberFormatException e) {
            plugin.getMessageService().send(player, "invalid-number");
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
            plugin.getMessageService().send(player, "terreno.listar.vazio");
            return true;
        }
        plugin.getMessageService().send(player, "terreno.listar.titulo");
        for (Terreno terreno : terrenos) {
            String pvp = terreno.getPvp() ? plugin.getMessageService().get("status.on") : plugin.getMessageService().get("status.off");
            String mobs = terreno.getMobs() ? plugin.getMessageService().get("status.on") : plugin.getMessageService().get("status.off");
            String line = plugin.getMessageService().format("terreno.listar.linha",
                    MessageService.placeholders(
                            "id", terreno.getId(),
                            "name", StringUtils.capitalizeFirstLetter(terreno.getName()),
                            "size", terreno.getSize(),
                            "pvp", pvp,
                            "mobs", mobs
                    ));
            plugin.getMessageService().send(player, line);
        }

        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            CompletableFuture<Terreno> asyncSearch = terrenoService.getCurrentTerrenoAsync(player);

            asyncSearch.thenAccept(terreno -> Bukkit.getScheduler().runTask(plugin, () -> exibirInfo(player, terreno))).exceptionally(
                    ex -> {
                        Bukkit.getScheduler().runTask(plugin, () -> plugin.getMessageService().send(player, "terreno.info.nao-no-terreno"));
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
            plugin.getMessageService().send(player, "terreno.info.nao-encontrado");
        }
        return true;
    }


    private void exibirInfo(Player player, Terreno t) {
        String donoNome = Bukkit.getOfflinePlayer(java.util.UUID.fromString(t.getDonoUUID())).getName();
        player.sendMessage(plugin.getMessageService().get("terreno.info.titulo"));
        player.sendMessage(plugin.getMessageService().format("terreno.info.dono", MessageService.placeholders("owner", (donoNome != null ? donoNome : t.getDonoUUID()))));
        player.sendMessage(plugin.getMessageService().format("terreno.info.nome", MessageService.placeholders("name", t.getName())));
        player.sendMessage(plugin.getMessageService().format("terreno.info.tamanho", MessageService.placeholders("size", t.getSize())));
        player.sendMessage(plugin.getMessageService().format("terreno.info.localizacao", MessageService.placeholders("location", LocationUtils.formattedLocation(t.getLocation()))));
        player.sendMessage(plugin.getMessageService().format("terreno.info.pvp", MessageService.placeholders("pvp", t.getPvp() ? plugin.getMessageService().get("status.habilitado") : plugin.getMessageService().get("status.desabilitado"))));
        player.sendMessage(plugin.getMessageService().format("terreno.info.mobs", MessageService.placeholders("mobs", t.getMobs() ? plugin.getMessageService().get("status.habilitado") : plugin.getMessageService().get("status.desabilitado"))));
        player.sendMessage(plugin.getMessageService().format("terreno.info.publico", MessageService.placeholders("public", t.getPublicAccess() ? plugin.getMessageService().get("status.sim") : plugin.getMessageService().get("status.nao"))));
        player.sendMessage(plugin.getMessageService().format("terreno.info.membros", MessageService.placeholders("count", t.getMembers().size())));
    }

    private boolean handleDeletar(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().send(player, "terreno.deletar.usage");
            return true;
        }

        try {
            String name = args[1];
            String playerUUID = player.getUniqueId().toString();
            Terreno terreno = terrenoService.buscarTerrenoPorNome(playerUUID, name);

            if (terrenoService.deletarTerreno(name, playerUUID)) {
                Location loc = LocationUtils.converterLocalizacao(terreno.getLocation());
                if (loc != null) {
                    FenceUtils.removerCercas(loc, terreno.getSize());
                }
                plugin.getMessageService().send(player, "terreno.deletar.sucesso");
            } else {
                plugin.getMessageService().send(player, "terreno.deletar.erro");
            }

        } catch (NumberFormatException e) {
            plugin.getMessageService().send(player, "terreno.deletar.id-invalido");
        } catch (TerrenoNotFoundException e) {
            plugin.getMessageService().send(player, "terreno.info.nao-encontrado");
            logger.warning(e.getMessage());
        }

        return true;
    }

    private boolean handleTogglePvp(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().send(player, "terreno.pvp.usage");
            return true;
        }

        return toggleSetting(player, args[1], "pvp");
    }

    private boolean handleToggleMobs(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().send(player, "terreno.mobs.usage");
            return true;
        }

        return toggleSetting(player, args[1], "mobs");
    }

    private boolean handleTogglePublico(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().send(player, "terreno.publico.usage");
            return true;
        }

        return toggleSetting(player, args[1], "publico");
    }

    private boolean toggleSetting(Player player, String nome, String setting) {
        try {
            String playerUUID = player.getUniqueId().toString();

            if (!terrenoService.isDono(nome, playerUUID)) {
                plugin.getMessageService().send(player, "terreno.toggle.nao-dono");
                return true;
            }

            boolean success = switch (setting) {
                case "pvp" -> terrenoService.togglePvp(nome, playerUUID);
                case "mobs" -> terrenoService.toggleMobs(nome, playerUUID);
                case "publico" -> terrenoService.togglePublico(nome, playerUUID);
                default -> false;
            };

            if (!success) {
                plugin.getMessageService().send(player, "terreno.toggle.erro");
                logger.warning("Erro ao atualizar configuração do terreno para " + setting);
                logger.warning("Jogador: " + player.getName() + " - Terreno: " + nome);
                return true;
            }

            Terreno atualizado = terrenoService.buscarTerrenoPorNome(playerUUID, nome);
            String mensagem = switch (setting) {
                case "pvp" -> plugin.getMessageService().format("terreno.toggle.pvp",
                        MessageService.placeholders("status",
                                atualizado.getPvp() ? plugin.getMessageService().get("status.habilitado")
                                        : plugin.getMessageService().get("status.desabilitado")));
                case "mobs" -> plugin.getMessageService().format("terreno.toggle.mobs",
                        MessageService.placeholders("status",
                                atualizado.getMobs() ? plugin.getMessageService().get("status.habilitado")
                                        : plugin.getMessageService().get("status.desabilitado")));
                case "publico" -> plugin.getMessageService().format("terreno.toggle.publico",
                        MessageService.placeholders("status",
                                atualizado.getPublicAccess() ? plugin.getMessageService().get("status.habilitado")
                                        : plugin.getMessageService().get("status.desabilitado")));
                default -> "";
            };

            player.sendMessage(mensagem);
            plugin.getMessageService().send(player, "terreno.toggle.sucesso", MessageService.placeholders("name", nome));
        } catch (TerrenoNotFoundException e) {
            plugin.getMessageService().send(player, "terreno.toggle.erro");
            logger.warning(e.getMessage());
        }

        return true;
    }

    private boolean handleTp(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().send(player, "terreno.tp.usage");
            return true;
        }
        try {
            String name = args[1];
            String playerUUID = player.getUniqueId().toString();

            if (!(terrenoService.isDono(name, playerUUID))) {
                plugin.getMessageService().send(player, "terreno.tp.sem-permissao");
                return true;
            }

            Optional<Location> safeLocOpt = terrenoService.getSafeTeleportLocation(name, playerUUID);
            if (safeLocOpt.isPresent()) {
                Location safe = safeLocOpt.get();
                player.teleport(safe);
                plugin.getMessageService().send(player, "terreno.tp.sucesso", MessageService.placeholders("name", name));
            } else {
                plugin.getMessageService().send(player, "terreno.tp.falha");
            }
        } catch (TerrenoNotFoundException e) {
            plugin.getMessageService().send(player, "terreno.info.nao-encontrado");
            logger.warning("[JocoTerrenos] TerrenoCommand: " + e.getMessage());
        }
        return true;
    }

    private boolean handlePreco(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().send(player, "terreno.preco.usage1");
            plugin.getMessageService().send(player, "terreno.preco.usage2");
            return true;
        }
        try {
            int tamanho = Integer.parseInt(args[1]);
            if (tamanho < terrenoService.getTamanhoMinimo()) {
                plugin.getMessageService().send(player, "terreno.preco.min", MessageService.placeholders("min", terrenoService.getTamanhoMinimo()));
                return true;
            }
            if (tamanho > terrenoService.getTamanhoMaximo()) {
                plugin.getMessageService().send(player, "terreno.preco.max", MessageService.placeholders("max", terrenoService.getTamanhoMaximo()));
                return true;
            }
            double custo = terrenoService.calcularCustoTerreno(tamanho);
            plugin.getMessageService().send(player, "terreno.preco.valor", MessageService.placeholders("size", tamanho, "price", String.format("%.2f", custo)));
        } catch (NumberFormatException e) {
            plugin.getMessageService().send(player, "invalid-number");
        }
        return true;
    }

    private void sendHelp(Player player) {
        plugin.getMessageService().send(player, "help.title");
        plugin.getMessageService().sendList(player, "help.lines");
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
