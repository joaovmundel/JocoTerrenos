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
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class TerrenoCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "jocoterrenos.terreno";
    private static final int MAX_NAME_LENGTH = 15;
    private static final int[] SUGGESTED_SIZES = {5, 10, 15, 20, 25, 30, 50, 100};
    private static final List<String> SUB_COMMANDS = Arrays.asList("comprar", "preco", "listar", "info", "deletar", "pvp", "mobs", "publico", "tp");

    private enum Toggle {
        PVP, MOBS, PUBLICO
    }

    private final JocoLogging logger = new JocoLogging(this.getClass().getName());
    private final JocoTerrenos plugin;
    private final TerrenoService terrenoService;

    public TerrenoCommand(JocoTerrenos plugin) {
        this.terrenoService = plugin.getTerrenoService();
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            plugin.getMessageService().send(sender, "no-permission");
            return true;
        }

        if (!(sender instanceof Player)) {
            plugin.getMessageService().send(sender, "only-player");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ENGLISH);
        if (subCommand.equals("comprar")) {
            return handleComprar(player, args);
        } else if (subCommand.equals("listar")) {
            return handleListar(player);
        } else if (subCommand.equals("info")) {
            return handleInfo(player, args);
        } else if (subCommand.equals("deletar")) {
            return handleDeletar(player, args);
        } else if (subCommand.equals("pvp")) {
            return handleToggle(player, args, Toggle.PVP, "terreno.pvp.usage");
        } else if (subCommand.equals("mobs")) {
            return handleToggle(player, args, Toggle.MOBS, "terreno.mobs.usage");
        } else if (subCommand.equals("publico")) {
            return handleToggle(player, args, Toggle.PUBLICO, "terreno.publico.usage");
        } else if (subCommand.equals("tp")) {
            return handleTp(player, args);
        } else if (subCommand.equals("preco")) {
            return handlePreco(player, args);
        }
        sendHelp(player);
        return true;
    }

    private boolean handleComprar(Player player, String[] args) {
        MessageService messages = plugin.getMessageService();
        if (args.length < 3) {
            String usageTitle = messages.get("terreno.comprar.usage-title");
            if (usageTitle != null && !usageTitle.isEmpty()) player.sendMessage(usageTitle);
            messages.send(player, "terreno.comprar.usage1");
            messages.send(player, "terreno.comprar.usage2");
            if (usageTitle != null && !usageTitle.isEmpty()) player.sendMessage(usageTitle);
            return true;
        }

        int tamanho;
        try {
            tamanho = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            messages.send(player, "invalid-number");
            return false;
        }
        String nome = joinArgs(args, 2);

        if (nome.length() > MAX_NAME_LENGTH) {
            messages.send(player, "terreno.comprar.nome-longo");
            return true;
        }

        if (!terrenoService.tamanhoValido(tamanho)) {
            messages.send(player, "terreno.comprar.tamanho-invalido-min", MessageService.placeholders("min", terrenoService.getTamanhoMinimo()));
            messages.send(player, "terreno.comprar.tamanho-invalido-max", MessageService.placeholders("max", terrenoService.getTamanhoMaximo()));
            return true;
        }

        int buffer = terrenoService.getEspacoEntreTerrenos();
        if (!terrenoService.isAreaDisponivel(player.getLocation(), tamanho, buffer)) {
            messages.send(player, "terreno.comprar.area-indisponivel", MessageService.placeholders("buffer", buffer));
            return true;
        }

        double custo = terrenoService.calcularCustoTerreno(tamanho);
        messages.send(player, "terreno.comprar.preco", MessageService.placeholders("price", String.format("%.2f", custo)));

        Terreno created = terrenoService.criarTerreno(player, tamanho, nome);
        if (created == null) {
            messages.send(player, "terreno.comprar.falha");
            return true;
        }

        FenceUtils.colocarCercas(player, tamanho);
        messages.send(player, "terreno.comprar.sucesso");
        messages.send(player, "terreno.comprar.info-dono", MessageService.placeholders("player", player.getName()));
        messages.send(player, "terreno.comprar.info-terreno", MessageService.placeholders("name", created.getName()));
        messages.send(player, "terreno.comprar.info-tamanho", MessageService.placeholders("size", tamanho));
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
        MessageService messages = plugin.getMessageService();
        List<Terreno> terrenos = terrenoService.listarTerrenosDoJogador(player.getName());

        if (terrenos.isEmpty()) {
            messages.send(player, "terreno.listar.vazio");
            return true;
        }
        messages.send(player, "terreno.listar.titulo");
        for (Terreno terreno : terrenos) {
            String pvp = terreno.getPvp() ? messages.get("status.on") : messages.get("status.off");
            String mobs = terreno.getMobs() ? messages.get("status.on") : messages.get("status.off");
            player.sendMessage(messages.format("terreno.listar.linha",
                    MessageService.placeholders(
                            "id", terreno.getId(),
                            "name", StringUtils.capitalizeFirstLetter(terreno.getName()),
                            "size", terreno.getSize(),
                            "pvp", pvp,
                            "mobs", mobs
                    )));
        }

        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        boolean atCurrentPosition = args.length < 2;
        try {
            Terreno terreno = atCurrentPosition
                    ? terrenoService.getCurrentTerreno(player)
                    : terrenoService.buscarTerrenoPorNome(player.getName(), joinArgs(args, 1));
            exibirInfo(player, terreno);
        } catch (TerrenoNotFoundException ex) {
            plugin.getMessageService().send(player, atCurrentPosition ? "terreno.info.nao-no-terreno" : "terreno.info.nao-encontrado");
        }
        return true;
    }

    private void exibirInfo(Player player, Terreno t) {
        MessageService messages = plugin.getMessageService();
        player.sendMessage(messages.get("terreno.info.titulo"));
        player.sendMessage(messages.format("terreno.info.dono", MessageService.placeholders("owner", t.getDonoNome())));
        player.sendMessage(messages.format("terreno.info.nome", MessageService.placeholders("name", t.getName())));
        player.sendMessage(messages.format("terreno.info.tamanho", MessageService.placeholders("size", t.getSize())));
        player.sendMessage(messages.format("terreno.info.localizacao", MessageService.placeholders("location", LocationUtils.formattedLocation(t.getLocation()))));
        player.sendMessage(messages.format("terreno.info.pvp", MessageService.placeholders("pvp", statusHabilitado(t.getPvp()))));
        player.sendMessage(messages.format("terreno.info.mobs", MessageService.placeholders("mobs", statusHabilitado(t.getMobs()))));
        player.sendMessage(messages.format("terreno.info.publico", MessageService.placeholders("public", t.getPublicAccess() ? messages.get("status.sim") : messages.get("status.nao"))));
        player.sendMessage(messages.format("terreno.info.membros", MessageService.placeholders("count", t.getMembers().size())));
    }

    private String statusHabilitado(boolean enabled) {
        return enabled ? plugin.getMessageService().get("status.habilitado") : plugin.getMessageService().get("status.desabilitado");
    }

    private boolean handleDeletar(Player player, String[] args) {
        MessageService messages = plugin.getMessageService();
        if (args.length < 2) {
            messages.send(player, "terreno.deletar.usage");
            return true;
        }

        try {
            String name = joinArgs(args, 1);
            Terreno terreno = terrenoService.buscarTerrenoPorNome(player.getName(), name);

            if (terrenoService.deletarTerreno(name, player.getName())) {
                Location loc = LocationUtils.converterLocalizacao(terreno.getLocation());
                if (loc != null) {
                    FenceUtils.removerCercas(loc, terreno.getSize());
                }
                messages.send(player, "terreno.deletar.sucesso");
            } else {
                messages.send(player, "terreno.deletar.erro");
            }
        } catch (TerrenoNotFoundException e) {
            messages.send(player, "terreno.info.nao-encontrado");
        }

        return true;
    }

    private boolean handleToggle(Player player, String[] args, Toggle toggle, String usageKey) {
        MessageService messages = plugin.getMessageService();
        if (args.length < 2) {
            messages.send(player, usageKey);
            return true;
        }
        String nome = joinArgs(args, 1);

        try {
            String donoNome = player.getName();

            if (!terrenoService.isDono(nome, donoNome)) {
                messages.send(player, "terreno.toggle.nao-dono");
                return true;
            }

            boolean success;
            switch (toggle) {
                case PVP:
                    success = terrenoService.togglePvp(nome, donoNome);
                    break;
                case MOBS:
                    success = terrenoService.toggleMobs(nome, donoNome);
                    break;
                default:
                    success = terrenoService.togglePublico(nome, donoNome);
                    break;
            }

            if (!success) {
                messages.send(player, "terreno.toggle.erro");
                logger.warning("Erro ao atualizar configuração do terreno para " + toggle);
                logger.warning("Jogador: " + player.getName() + " - Terreno: " + nome);
                return true;
            }

            Terreno atualizado = terrenoService.buscarTerrenoPorNome(donoNome, nome);
            switch (toggle) {
                case PVP:
                    player.sendMessage(messages.format("terreno.toggle.pvp", MessageService.placeholders("status", statusHabilitado(atualizado.getPvp()))));
                    break;
                case MOBS:
                    player.sendMessage(messages.format("terreno.toggle.mobs", MessageService.placeholders("status", statusHabilitado(atualizado.getMobs()))));
                    break;
                default:
                    player.sendMessage(messages.format("terreno.toggle.publico", MessageService.placeholders("status", statusHabilitado(atualizado.getPublicAccess()))));
                    break;
            }
            messages.send(player, "terreno.toggle.sucesso", MessageService.placeholders("name", nome));
        } catch (TerrenoNotFoundException e) {
            messages.send(player, "terreno.info.nao-encontrado");
        }

        return true;
    }

    private boolean handleTp(Player player, String[] args) {
        MessageService messages = plugin.getMessageService();
        if (args.length < 2) {
            messages.send(player, "terreno.tp.usage");
            return true;
        }
        try {
            String name = joinArgs(args, 1);

            if (!terrenoService.isDono(name, player.getName())) {
                messages.send(player, "terreno.tp.sem-permissao");
                return true;
            }

            Location safe = terrenoService.getSafeTeleportLocation(name, player.getName());
            if (safe != null) {
                player.teleport(safe);
                messages.send(player, "terreno.tp.sucesso", MessageService.placeholders("name", name));
            } else {
                messages.send(player, "terreno.tp.falha");
            }
        } catch (TerrenoNotFoundException e) {
            messages.send(player, "terreno.info.nao-encontrado");
        }
        return true;
    }

    private boolean handlePreco(Player player, String[] args) {
        MessageService messages = plugin.getMessageService();
        if (args.length < 2) {
            messages.send(player, "terreno.preco.usage1");
            messages.send(player, "terreno.preco.usage2");
            return true;
        }
        try {
            int tamanho = Integer.parseInt(args[1]);
            if (tamanho < terrenoService.getTamanhoMinimo()) {
                messages.send(player, "terreno.preco.min", MessageService.placeholders("min", terrenoService.getTamanhoMinimo()));
                return true;
            }
            if (tamanho > terrenoService.getTamanhoMaximo()) {
                messages.send(player, "terreno.preco.max", MessageService.placeholders("max", terrenoService.getTamanhoMaximo()));
                return true;
            }
            double custo = terrenoService.calcularCustoTerreno(tamanho);
            messages.send(player, "terreno.preco.valor", MessageService.placeholders("size", tamanho, "price", String.format("%.2f", custo)));
        } catch (NumberFormatException e) {
            messages.send(player, "invalid-number");
        }
        return true;
    }

    private void sendHelp(Player player) {
        plugin.getMessageService().send(player, "help.title");
        plugin.getMessageService().sendList(player, "help.lines");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<String>();
        if (!(sender instanceof Player)) {
            return suggestions;
        }
        Player player = (Player) sender;

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ENGLISH);
            for (String sub : SUB_COMMANDS) {
                if (sub.startsWith(prefix)) suggestions.add(sub);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ENGLISH);
            String prefix = args[1].toLowerCase(Locale.ENGLISH);
            if (sub.equals("comprar") || sub.equals("preco")) {
                for (int size : SUGGESTED_SIZES) {
                    String s = String.valueOf(size);
                    if (s.startsWith(prefix)) suggestions.add(s);
                }
            } else if (sub.equals("info") || sub.equals("deletar") || sub.equals("pvp") || sub.equals("mobs")
                    || sub.equals("publico") || sub.equals("tp")) {
                addTerrenoNames(suggestions, player, prefix);
            }
        } else if (args.length >= 3 && args[0].equalsIgnoreCase("comprar")) {
            addTerrenoNames(suggestions, player, joinArgs(args, 2).toLowerCase(Locale.ENGLISH));
        }

        return suggestions;
    }

    private void addTerrenoNames(List<String> suggestions, Player player, String lowercasePrefix) {
        for (Terreno t : terrenoService.listarTerrenosDoJogador(player.getName())) {
            String name = t.getName();
            if (name != null && name.toLowerCase(Locale.ENGLISH).startsWith(lowercasePrefix)) suggestions.add(name);
        }
    }
}
