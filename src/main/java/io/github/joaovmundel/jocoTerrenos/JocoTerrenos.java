package io.github.joaovmundel.jocoTerrenos;

import io.github.joaovmundel.jocoTerrenos.commands.CercarCommand;
import io.github.joaovmundel.jocoTerrenos.commands.ResizeCercaCommand;
import io.github.joaovmundel.jocoTerrenos.commands.RemoverCercaCommand;
import io.github.joaovmundel.jocoTerrenos.commands.TerrenoCommand;
import io.github.joaovmundel.jocoTerrenos.database.DatabaseManager;
import io.github.joaovmundel.jocoTerrenos.repositories.TerrenoRepository;
import io.github.joaovmundel.jocoTerrenos.service.TerrenoService;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.joaovmundel.jocoTerrenos.service.MessageService;

@Getter
public final class JocoTerrenos extends JavaPlugin {

    private DatabaseManager databaseManager;
    private TerrenoRepository terrenoRepository;
    private TerrenoService terrenoService;
    private Economy economy;
    private MessageService messageService;

    @Override
    public void onEnable() {
        // Salva a configuração padrão se não existir
        saveDefaultConfig();

        // Inicializa MessageService e arquivos de idioma
        messageService = new MessageService(this);
        messageService.initLocalesFolderAndDefaults();
        messageService.reload();

        // Inicializa o banco de dados
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        // Inicializa os repositórios
        terrenoRepository = new TerrenoRepository(databaseManager, getLogger());

        // Inicializa os services
        terrenoService = new TerrenoService(terrenoRepository, getConfig());

        // Registra os comandos
        getCommand("cercar").setExecutor(new CercarCommand(messageService));
        getCommand("resizecerca").setExecutor(new ResizeCercaCommand(messageService));
        getCommand("removercerca").setExecutor(new RemoverCercaCommand(messageService));
        TerrenoCommand terrenoCmd = new TerrenoCommand(this);
        getCommand("terreno").setExecutor(terrenoCmd);
        getCommand("terreno").setTabCompleter(terrenoCmd);

        // Setup Vault Economy
        setupEconomy();

        getLogger().info("JocoTerrenos habilitado com sucesso!");
    }

    @Override
    public void onDisable() {
        // Fecha a conexão com o banco de dados
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("JocoTerrenos desabilitado!");
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault não encontrado! Desabilitando o plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("Provider de Economy não encontrado! Desabilitando o plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        economy = rsp.getProvider();
        if (economy == null) {
            getLogger().severe("Economy provider é null! Desabilitando o plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Economy inicializado com sucesso: " + economy.getName());
    }
}
