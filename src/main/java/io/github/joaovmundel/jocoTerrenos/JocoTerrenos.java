package io.github.joaovmundel.jocoTerrenos;

import io.github.joaovmundel.jocoTerrenos.commands.CercarCommand;
import io.github.joaovmundel.jocoTerrenos.commands.ResizeCercaCommand;
import io.github.joaovmundel.jocoTerrenos.commands.RemoverCercaCommand;
import io.github.joaovmundel.jocoTerrenos.commands.TerrenoCommand;
import io.github.joaovmundel.jocoTerrenos.database.DatabaseManager;
import io.github.joaovmundel.jocoTerrenos.repositories.TerrenoRepository;
import io.github.joaovmundel.jocoTerrenos.service.TerrenoService;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class JocoTerrenos extends JavaPlugin {

    private DatabaseManager databaseManager;
    private TerrenoRepository terrenoRepository;
    private TerrenoService terrenoService;

    @Override
    public void onEnable() {
        // Salva a configuração padrão se não existir
        saveDefaultConfig();

        // Inicializa o banco de dados
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        // Inicializa os repositórios
        terrenoRepository = new TerrenoRepository(databaseManager, getLogger());

        // Inicializa os services
        terrenoService = new TerrenoService(terrenoRepository, getConfig());

        // Registra os comandos
        getCommand("cercar").setExecutor(new CercarCommand());
        getCommand("resizecerca").setExecutor(new ResizeCercaCommand());
        getCommand("removercerca").setExecutor(new RemoverCercaCommand());
        getCommand("terreno").setExecutor(new TerrenoCommand(this));

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

}

