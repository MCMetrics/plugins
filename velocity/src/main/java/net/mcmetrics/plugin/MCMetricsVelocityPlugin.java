package net.mcmetrics.plugin;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.mcmetrics.plugin.commands.MCMetricsCommand;
import net.mcmetrics.plugin.listeners.PlayerSessionListener;
import net.mcmetrics.shared.MCMetricsAPI;
import net.mcmetrics.shared.config.ConfigManager;
import net.mcmetrics.shared.models.ServerPing;

import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Plugin(
        id = "mcmetrics",
        name = "MCMetrics",
        version = "@VERSION@",
        description = "The MCMetrics Velocity plugin",
        authors = {"MCMetrics Team"}
)
public class MCMetricsVelocityPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private ConfigManager configManager;
    private MCMetricsAPI api;
    private SessionManager sessionManager;

    @Inject
    public MCMetricsVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.configManager = new ConfigManager();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            InputStream defaultConfig = getClass().getResourceAsStream("/config.yml");
            configManager.loadConfig("main", dataDirectory.toFile(), "config.yml", defaultConfig, logger);
            initializeAPI();
            sessionManager = new SessionManager(api);

            server.getEventManager().register(this, new PlayerSessionListener(this, sessionManager));

            // Register the command
            MCMetricsCommand mcMetricsCommand = new MCMetricsCommand(this);
            CommandMeta meta = server.getCommandManager().metaBuilder("mcmetricsv")
                    .aliases("mcmv", "mcmetricsvelocity")
                    .plugin(this)
                    .build();
            server.getCommandManager().register(meta, (SimpleCommand) mcMetricsCommand::execute);

            startServerPingTask();
            logger.info("MCMetrics plugin has been enabled. Thank you for using MCMetrics!");
        } catch (IOException e) {
            logger.severe("Failed to load configuration: " + e.getMessage());
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (sessionManager != null) {
            sessionManager.endAllSessions().forEach(session -> {
                session.session_end = new Date();
                api.insertSession(session).join(); // Wait for each session to be inserted
            });
        }
        logger.info("MCMetrics plugin disabled.");
    }

    private void initializeAPI() {
        String serverId = configManager.getString("main", "server.id");
        String serverKey = configManager.getString("main", "server.key");

        if (serverId == null || serverKey == null || serverId.isEmpty() || serverKey.isEmpty()) {
            logger.warning("Server ID or Server Key not set in config.yml. Please use /mcmetricsvelocity setup to configure the plugin.");
            return;
        }

        api = new MCMetricsAPI(serverId, serverKey, logger);
    }

    private void startServerPingTask() {
        server.getScheduler()
                .buildTask(this, this::recordServerPing)
                .repeat(60L, TimeUnit.SECONDS)
                .schedule();
    }

    private void recordServerPing() {
        if (api == null) {
            logger.warning("MCMetrics API is not initialized. Please use /mcmetricsvelocity setup to configure the plugin.");
            return;
        }

        ServerPing ping = new ServerPing();
        ping.time = new Date();
        ping.player_count = server.getPlayerCount();
        
        // Count Bedrock players by UUID format
        int bedrockCount = (int) server.getAllPlayers().stream()
            .filter(player -> player.getUniqueId().toString().startsWith("00000000-0000-0000"))
            .count();
            
        ping.bedrock_player_count = bedrockCount;
        ping.java_player_count = ping.player_count - bedrockCount;
        api.insertServerPing(ping).thenRun(() -> logger.info("Server ping recorded successfully."));
    }

    public MCMetricsAPI getApi() {
        return api;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public void reloadPlugin() {
        try {
            configManager.reloadConfig("main");
            initializeAPI();
            logger.info("MCMetrics configuration reloaded.");
        } catch (IOException e) {
            logger.severe("Failed to reload configuration: " + e.getMessage());
        }
    }

    public int getActiveSessionCount() {
        return sessionManager.getActiveSessionCount();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }
}