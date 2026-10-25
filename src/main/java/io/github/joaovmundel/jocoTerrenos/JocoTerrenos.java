package io.github.joaovmundel.jocoTerrenos;

import io.github.joaovmundel.jocoTerrenos.commands.CercarCommand;
import io.github.joaovmundel.jocoTerrenos.commands.RemoverCercaCommand;
import io.github.joaovmundel.jocoTerrenos.commands.ResizeCercaCommand;
import io.github.joaovmundel.jocoTerrenos.commands.TerrenoCommand;
import io.github.joaovmundel.jocoTerrenos.database.DatabaseManager;
import io.github.joaovmundel.jocoTerrenos.economy.EconomyGateway;
import io.github.joaovmundel.jocoTerrenos.economy.VaultEconomyGateway;
import io.github.joaovmundel.jocoTerrenos.exceptions.StorageException;
import io.github.joaovmundel.jocoTerrenos.infrastructure.JocoLogging;
import io.github.joaovmundel.jocoTerrenos.listener.BuildProtectionListener;
import io.github.joaovmundel.jocoTerrenos.listener.EntityProtectionListener;
import io.github.joaovmundel.jocoTerrenos.listener.EnvironmentProtectionListener;
import io.github.joaovmundel.jocoTerrenos.protection.ClaimIndex;
import io.github.joaovmundel.jocoTerrenos.repositories.TerrenoRepository;
import io.github.joaovmundel.jocoTerrenos.service.MessageService;
import io.github.joaovmundel.jocoTerrenos.service.ProtectionService;
import io.github.joaovmundel.jocoTerrenos.service.TerrenoService;
import lombok.Getter;
import org.bukkit.plugin.PluginManager;
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
    private MessageService messageService;

    @Override
    public void onEnable() {
        // Salva a configuração padrão se não existir
        saveDefaultConfig();
        JocoLogging.setDebugEnabled(getConfig().getBoolean("debug", false));

        // Inicializa MessageService e arquivos de idioma
        messageService = new MessageService(this);
        messageService.initLocalesFolderAndDefaults();
        messageService.reload();

        // Inicializa o banco de dados
        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.initialize();
        } catch (StorageException e) {
            abortStartup("Banco de dados indisponivel; sem ele os terrenos ficariam sem protecao.", e);
            return;
        }

        // Inicializa os repositórios
        terrenoRepository = new TerrenoRepository(databaseManager.getConnectionProvider(), getLogger());

        // Inicializa os services
        claimIndex = new ClaimIndex();
        EconomyGateway economy = new VaultEconomyGateway();
        terrenoService = new TerrenoService(terrenoRepository, getConfig(), claimIndex, economy, getLogger());
        try {
            terrenoService.reloadClaims();
        } catch (StorageException e) {
            abortStartup("Nao foi possivel carregar os terrenos; sem eles nada seria protegido.", e);
            return;
        }
        getLogger().info(claimIndex.size() + " terreno(s) carregado(s) na protecao.");

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

        if (!economy.isAvailable()) {
            getLogger().warning("Nenhuma economia registrada no Vault ainda; as compras de terreno ficam indisponiveis ate uma ser registrada.");
        }
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
        getLogger().log(Level.SEVERE, message + " O plugin sera desabilitado.", cause);
        getServer().getPluginManager().disablePlugin(this);
    }
}
