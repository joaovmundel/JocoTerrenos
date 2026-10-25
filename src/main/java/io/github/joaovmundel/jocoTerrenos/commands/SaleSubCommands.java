package io.github.joaovmundel.jocoTerrenos.commands;

import io.github.joaovmundel.jocoTerrenos.JocoTerrenos;
import io.github.joaovmundel.jocoTerrenos.economy.SalePrice;
import io.github.joaovmundel.jocoTerrenos.exceptions.TerrenoNotFoundException;
import io.github.joaovmundel.jocoTerrenos.models.Terreno;
import io.github.joaovmundel.jocoTerrenos.service.MessageService;
import io.github.joaovmundel.jocoTerrenos.service.PurchaseOutcome;
import io.github.joaovmundel.jocoTerrenos.service.TerrenoService;
import io.github.joaovmundel.jocoTerrenos.utils.LocationUtils;
import io.github.joaovmundel.jocoTerrenos.utils.LocationUtils.LocationRaw;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The selling side of {@code /terreno}: {@code vender}, {@code cancelarvenda}, {@code adquirir} and
 * {@code vendas}. It only reads the arguments and shows results; the rules live in {@link TerrenoService}.
 */
final class SaleSubCommands {

    static final List<String> NAMES = Arrays.asList("vender", "cancelarvenda", "adquirir", "vendas");

    /** How many lands {@code /terreno vendas} and the tab completion look at. */
    private static final int MARKET_SIZE = 10;
    private static final int COMPLETION_SIZE = 50;

    private final JocoTerrenos plugin;
    private final TerrenoService terrenoService;

    SaleSubCommands(JocoTerrenos plugin) {
        this.plugin = plugin;
        this.terrenoService = plugin.getTerrenoService();
    }

    static boolean handles(String subCommand) {
        return NAMES.contains(subCommand.toLowerCase(Locale.ENGLISH));
    }

    /** @return whether the sub command was one of ours */
    boolean execute(Player player, String subCommand, String[] args) {
        if (subCommand.equals("vender")) {
            vender(player, args);
        } else if (subCommand.equals("cancelarvenda")) {
            cancelarVenda(player, args);
        } else if (subCommand.equals("adquirir")) {
            adquirir(player, args);
        } else if (subCommand.equals("vendas")) {
            vendas(player);
        } else {
            return false;
        }
        return true;
    }

    /** /terreno vender &lt;nome&gt; &lt;valor&gt; : the price is the last word, the name is everything before it. */
    private void vender(Player player, String[] args) {
        MessageService messages = plugin.getMessageService();
        if (args.length < 3) {
            messages.send(player, "terreno.vender.usage1");
            messages.send(player, "terreno.vender.usage2");
            return;
        }
        BigDecimal maximo = terrenoService.getPrecoMaximoVenda();
        BigDecimal preco = SalePrice.parse(args[args.length - 1], maximo);
        if (preco == null) {
            messages.send(player, "terreno.vender.valor-invalido", MessageService.placeholders("max", terrenoService.formatarPreco(maximo)));
            return;
        }
        String nome = Args.join(args, 1, args.length - 1);

        try {
            TerrenoService.SaleResult resultado = terrenoService.colocarAVenda(player.getName(), nome, preco);
            if (resultado != TerrenoService.SaleResult.OK) {
                messages.send(player, "terreno.toggle.erro");
                return;
            }
            messages.send(player, "terreno.vender.sucesso",
                    MessageService.placeholders("name", nome, "price", terrenoService.formatarPreco(preco)));
            messages.send(player, "terreno.vender.dica",
                    MessageService.placeholders("name", nome, "owner", player.getName()));
            messages.send(player, "terreno.vender.aviso-entrada");
        } catch (TerrenoNotFoundException e) {
            messages.send(player, "terreno.info.nao-encontrado");
        }
    }

    private void cancelarVenda(Player player, String[] args) {
        MessageService messages = plugin.getMessageService();
        if (args.length < 2) {
            messages.send(player, "terreno.cancelarvenda.usage");
            return;
        }
        String nome = Args.join(args, 1, args.length);

        try {
            TerrenoService.SaleResult resultado = terrenoService.cancelarVenda(player.getName(), nome);
            if (resultado == TerrenoService.SaleResult.OK) {
                messages.send(player, "terreno.cancelarvenda.sucesso", MessageService.placeholders("name", nome));
            } else if (resultado == TerrenoService.SaleResult.NOT_LISTED) {
                messages.send(player, "terreno.cancelarvenda.nao-esta-a-venda", MessageService.placeholders("name", nome));
            } else {
                messages.send(player, "terreno.toggle.erro");
            }
        } catch (TerrenoNotFoundException e) {
            messages.send(player, "terreno.info.nao-encontrado");
        }
    }

    /**
     * /terreno adquirir : buys the land the player is standing on.
     * /terreno adquirir &lt;dono&gt; &lt;nome&gt; : buys that player's land from anywhere.
     */
    private void adquirir(Player player, String[] args) {
        MessageService messages = plugin.getMessageService();
        if (args.length == 2) {
            messages.send(player, "terreno.adquirir.usage1");
            messages.send(player, "terreno.adquirir.usage2");
            return;
        }

        Terreno terreno;
        try {
            terreno = args.length == 1
                    ? terrenoService.getCurrentTerreno(player)
                    : terrenoService.buscarTerrenoPorNome(args[1], Args.join(args, 2, args.length));
        } catch (TerrenoNotFoundException e) {
            messages.send(player, args.length == 1 ? "terreno.info.nao-no-terreno" : "terreno.info.nao-encontrado");
            return;
        }

        PurchaseOutcome resultado = terrenoService.adquirir(player, terreno);
        switch (resultado.getStatus()) {
            case SUCCESS:
                announcePurchase(player, resultado);
                break;
            case NOT_FOR_SALE:
                messages.send(player, "terreno.adquirir.nao-a-venda");
                break;
            case OWN_LAND:
                messages.send(player, "terreno.adquirir.proprio");
                break;
            case ECONOMY_UNAVAILABLE:
                messages.send(player, "terreno.adquirir.economia-indisponivel");
                break;
            case INSUFFICIENT_FUNDS:
                messages.send(player, "terreno.adquirir.saldo-insuficiente",
                        MessageService.placeholders("price", terrenoService.formatarPreco(resultado.getPrice())));
                break;
            case NAME_IN_USE:
                messages.send(player, "terreno.adquirir.nome-em-uso", MessageService.placeholders("name", terreno.getName()));
                break;
            case SAVE_FAILED:
                messages.send(player, "terreno.adquirir.erro-salvar");
                break;
            default:
                messages.send(player, "terreno.adquirir.erro-pagamento");
                break;
        }
    }

    private void announcePurchase(Player buyer, PurchaseOutcome resultado) {
        MessageService messages = plugin.getMessageService();
        Terreno terreno = resultado.getTerreno();
        String preco = terrenoService.formatarPreco(resultado.getPrice());

        messages.send(buyer, "terreno.adquirir.sucesso", MessageService.placeholders(
                "name", terreno.getName(), "seller", resultado.getPreviousOwner(), "price", preco));
        if (resultado.wasRenamed()) {
            messages.send(buyer, "terreno.adquirir.renomeado", MessageService.placeholders("name", terreno.getName()));
        }

        Player seller = plugin.getServer().getPlayerExact(resultado.getPreviousOwner());
        if (seller != null) {
            messages.send(seller, "terreno.adquirir.aviso-vendedor", MessageService.placeholders(
                    "buyer", buyer.getName(), "name", terreno.getName(), "price", preco));
        }
        // the old owner and members are strangers now: if new lands start private, nobody is left stuck inside
        plugin.getProtectionService().expelIntruders(terreno.getId());
    }

    private void vendas(Player player) {
        MessageService messages = plugin.getMessageService();
        List<Terreno> mercado = terrenoService.listarAVenda(MARKET_SIZE);
        if (mercado.isEmpty()) {
            messages.send(player, "terreno.vendas.vazio");
            return;
        }
        messages.send(player, "terreno.vendas.titulo");
        for (Terreno terreno : mercado) {
            player.sendMessage(messages.format("terreno.vendas.linha", MessageService.placeholders(
                    "id", terreno.getId(),
                    "name", terreno.getName(),
                    "owner", terreno.getDonoNome(),
                    "size", terreno.getSize(),
                    "price", terrenoService.formatarPreco(terreno.getSalePrice()),
                    "where", describeLocation(terreno))));
        }
        messages.send(player, "terreno.vendas.rodape");
    }

    private static String describeLocation(Terreno terreno) {
        LocationRaw raw = LocationUtils.converterLocalizacaoRaw(terreno.getLocation());
        if (raw == null) {
            return "?";
        }
        return raw.worldName() + " " + LocationUtils.formattedLocation(terreno.getLocation());
    }

    /** Tab completion for the sale sub commands; {@code args[0]} is one of {@link #NAMES}. */
    List<String> complete(Player player, String[] args) {
        List<String> suggestions = new ArrayList<String>();
        String sub = args[0].toLowerCase(Locale.ENGLISH);

        if (sub.equals("vender") && args.length == 2) {
            addNames(suggestions, terrenoService.listarTerrenosDoJogador(player.getName()), args[1], false);
        } else if (sub.equals("cancelarvenda") && args.length >= 2) {
            addNames(suggestions, terrenoService.listarTerrenosDoJogador(player.getName()), Args.join(args, 1, args.length), true);
        } else if (sub.equals("adquirir") && args.length == 2) {
            Set<String> owners = new LinkedHashSet<String>();
            for (Terreno terreno : terrenoService.listarAVenda(COMPLETION_SIZE)) {
                owners.add(terreno.getDonoNome());
            }
            for (String owner : owners) {
                if (owner.startsWith(args[1].toLowerCase(Locale.ENGLISH))) suggestions.add(owner);
            }
        } else if (sub.equals("adquirir") && args.length >= 3) {
            List<Terreno> daqueleDono = new ArrayList<Terreno>();
            for (Terreno terreno : terrenoService.listarAVenda(COMPLETION_SIZE)) {
                if (terreno.getDonoNome().equalsIgnoreCase(args[1])) daqueleDono.add(terreno);
            }
            addNames(suggestions, daqueleDono, Args.join(args, 2, args.length), false);
        }
        return suggestions;
    }

    private static void addNames(List<String> suggestions, List<Terreno> terrenos, String typed, boolean onlyForSale) {
        String prefix = typed.toLowerCase(Locale.ENGLISH);
        for (Terreno terreno : terrenos) {
            if (onlyForSale && terreno.getSalePrice() == null) {
                continue;
            }
            String name = terreno.getName();
            if (name != null && name.toLowerCase(Locale.ENGLISH).startsWith(prefix)) suggestions.add(name);
        }
    }
}
