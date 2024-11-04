package net.mcmetrics.plugin;

import net.mcmetrics.plugin.commands.MCMetricsCommand;
import net.mcmetrics.plugin.listeners.PlayerSessionListener;
import net.md_5.bungee.api.plugin.Plugin;
import net.mcmetrics.shared.MCMetricsAPI;
import net.mcmetrics.shared.config.ConfigManager;
import net.mcmetrics.shared.models.ServerPing;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public final class MCMetricsBungeePlugin extends Plugin {
    private MCMetricsAPI api;
    private SessionManager sessionManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager();
        try {
            configManager.loadConfig("main", getDataFolder(), "config.yml", 
                getResourceAsStream("config.yml"), getLogger());
        } catch (IOException e) {
            getLogger().severe("Failed to load configuration: " + e.getMessage());
            return;
        }

        initializeAPI();
        sessionManager = new SessionManager(api);
        getProxy().getPluginManager().registerListener(this, new PlayerSessionListener(this, sessionManager));
        getProxy().getPluginManager().registerCommand(this, new MCMetricsCommand(this));
        startServerPingTask();
        getLogger().info("MCMetrics plugin enabled successfully!");
    }

    public void initializeAPI() {
        String serverId = configManager.getString("main", "server.id");
        String serverKey = configManager.getString("main", "server.key");

        if (serverId == null || serverKey == null || serverId.isEmpty() || serverKey.isEmpty()) {
            getLogger().warning("Server ID or Server Key not set in config.yml. Please use /mcmetricsbungee setup to configure the plugin.");
            return;
        }

        api = new MCMetricsAPI(serverId, serverKey, getLogger());
    }

    @Override
    public void onDisable() {
        if (sessionManager != null) {
            sessionManager.endAllSessions().forEach(session -> {
                session.session_end = new Date();
                api.insertSession(session).join(); // Wait for each session to be inserted
            });
        }
        getLogger().info("MCMetrics plugin disabled.");
    }

    private void startServerPingTask() {
        getProxy().getScheduler().schedule(this, () -> {
            if (api == null) {
                getLogger().warning("MCMetrics API is not initialized. Please use /mcmetricsbungee setup to configure the plugin.");
                return;
            }

            ServerPing ping = new ServerPing();
            ping.time = new Date();
            ping.player_count = getProxy().getOnlineCount();
            ping.java_player_count = ping.player_count; // BungeeCord doesn't distinguish between Java and Bedrock players
            ping.bedrock_player_count = 0;
            ping.tps = -1.0;
            ping.mspt = -1.0; // BungeeCord doesn't have MSPT
            ping.cpu_percent = 0.0; // Implement CPU usage calculation if possible
            ping.ram_percent = 0.0; // Implement RAM usage calculation if possible
            ping.entities_loaded = -1; // Not applicable for BungeeCord
            ping.chunks_loaded = -1; // Not applicable for BungeeCord

            api.insertServerPing(ping).thenRun(() -> 
                getLogger().info("Server ping recorded successfully."));
        }, 0, 10, TimeUnit.SECONDS);
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
            getLogger().info("MCMetrics configuration reloaded.");
        } catch (IOException e) {
            getLogger().severe("Failed to reload configuration: " + e.getMessage());
        }
    }

    public int getActiveSessionCount() {
        return sessionManager.getActiveSessionCount();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}