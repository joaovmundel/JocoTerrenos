package io.github.joaovmundel.jocoTerrenos;

import io.github.joaovmundel.jocoTerrenos.commands.CercarCommand;
import io.github.joaovmundel.jocoTerrenos.commands.RemoverCercaCommand;
import io.github.joaovmundel.jocoTerrenos.commands.ResizeCercaCommand;
import io.github.joaovmundel.jocoTerrenos.commands.TerrenoCommand;
import io.github.joaovmundel.jocoTerrenos.database.DatabaseManager;
import io.github.joaovmundel.jocoTerrenos.exceptions.StorageException;
import io.github.joaovmundel.jocoTerrenos.listener.BuildProtectionListener;
import io.github.joaovmundel.jocoTerrenos.listener.EntityProtectionListener;
import io.github.joaovmundel.jocoTerrenos.listener.EnvironmentProtectionListener;
import io.github.joaovmundel.jocoTerrenos.protection.ClaimIndex;
import io.github.joaovmundel.jocoTerrenos.repositories.TerrenoRepository;
import io.github.joaovmundel.jocoTerrenos.service.MessageService;
import io.github.joaovmundel.jocoTerrenos.service.ProtectionService;
import io.github.joaovmundel.jocoTerrenos.service.TerrenoService;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

@Getter
public final class JocoTerrenos extends JavaPlugin {

    private static final long DEFAULT_MESSAGE_COOLDOWN_MILLIS = 1500L;

    private DatabaseManager databaseManager;
    private TerrenoRepository terrenoRepository;
    private TerrenoService terrenoService;
    private ClaimIndex claimIndex;
    private ProtectionService protectionService;
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
        if (!databaseManager.isConnected()) {
            abortStartup("Banco de dados indisponível; sem ele os terrenos ficariam sem proteção.", null);
            return;
        }

        // Inicializa os repositórios
        terrenoRepository = new TerrenoRepository(databaseManager, getLogger());

        // Inicializa os services
        claimIndex = new ClaimIndex();
        terrenoService = new TerrenoService(terrenoRepository, getConfig(), messageService, claimIndex, getLogger());
        try {
            terrenoService.reloadClaims();
        } catch (StorageException e) {
            abortStartup("Não foi possível carregar os terrenos; sem eles nada seria protegido.", e);
            return;
        }
        getLogger().info(claimIndex.size() + " terreno(s) carregado(s) na proteção.");

        protectionService = new ProtectionService(claimIndex, messageService,
                getConfig().getLong("protection.message-cooldown-ms", DEFAULT_MESSAGE_COOLDOWN_MILLIS));
        registerProtectionListeners();

        // Registra os comandos
        Objects.requireNonNull(getCommand("cercar")).setExecutor(new CercarCommand(messageService));
        Objects.requireNonNull(getCommand("resizecerca")).setExecutor(new ResizeCercaCommand(messageService));
        Objects.requireNonNull(getCommand("removercerca")).setExecutor(new RemoverCercaCommand(messageService));
        TerrenoCommand terrenoCmd = new TerrenoCommand(this);
        Objects.requireNonNull(getCommand("terreno")).setExecutor(terrenoCmd);
        Objects.requireNonNull(getCommand("terreno")).setTabCompleter(terrenoCmd);

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

    private void registerProtectionListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new BuildProtectionListener(protectionService), this);
        pluginManager.registerEvents(new EntityProtectionListener(protectionService), this);
        pluginManager.registerEvents(new EnvironmentProtectionListener(protectionService), this);
    }

    /** Fails fast: a plugin that cannot protect lands must not keep selling them. */
    private void abortStartup(String message, Exception cause) {
        getLogger().log(Level.SEVERE, message + " O plugin será desabilitado.", cause);
        getServer().getPluginManager().disablePlugin(this);
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
        getLogger().info("Economy inicializado com sucesso: " + economy.getName());
    }
}
