package net.mcmetrics.plugin;

import net.mcmetrics.plugin.commands.MCMetricsCommand;
import net.mcmetrics.plugin.listeners.PlayerSessionListener;
import net.md_5.bungee.api.plugin.Plugin;
import net.mcmetrics.shared.MCMetricsAPI;
import net.mcmetrics.shared.config.ConfigManager;
import net.mcmetrics.shared.models.ServerPing;
import net.mcmetrics.shared.models.Session;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.io.IOException;
import java.util.Date;
import java.util.List;
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
        getLogger().info("MCMetrics plugin has been enabled. Thank you for using MCMetrics!");
    }

    public void initializeAPI() {
        String serverId = configManager.getString("main", "server.id");
        String serverKey = configManager.getString("main", "server.key");

        if (serverId == null || serverKey == null || serverId.isEmpty() || serverKey.isEmpty()) {
            getLogger().warning(
                    "Server ID or Server Key not set in config.yml. Please use /mcmetricsbungee setup to configure the plugin.");
            return;
        }

        api = new MCMetricsAPI(serverId, serverKey, getLogger());
    }

    @Override
    public void onDisable() {
        if (sessionManager != null) {
            List<Session> remainingSessions = sessionManager.endAllSessions();
            if (!remainingSessions.isEmpty()) {
                getLogger().info("Ending " + remainingSessions.size() + " remaining sessions...");

                // Set end time for all sessions
                Date endTime = new Date();
                remainingSessions.forEach(session -> session.session_end = endTime);

                // Upload sessions asynchronously in batches
                if (api != null) {
                    try {
                        api.insertSessionsBatch(remainingSessions, 5, 20) // 5 sessions per batch, 20 second timeout
                                .get(25, TimeUnit.SECONDS); // Wait up to 25 seconds for all uploads to complete
                        getLogger().info("Successfully uploaded all remaining sessions during shutdown");
                    } catch (Exception e) {
                        getLogger().warning("Failed to upload some sessions during shutdown: " + e.getMessage());
                    }
                }
            }
        }

        if (api != null) {
            api.shutdown();
        }
        getLogger().info("MCMetrics plugin disabled.");
    }

    private void startServerPingTask() {
        getProxy().getScheduler().schedule(this, () -> {
            if (api == null) {
                getLogger().warning(
                        "MCMetrics API is not initialized. Please use /mcmetricsbungee setup to configure the plugin.");
                return;
            }

            ServerPing ping = new ServerPing();
            ping.time = new Date();

            double playerCountMultiplier = configManager.getDouble("main", "playercount-multiplier");
            int playerCountSubtract = configManager.getInt("main", "playercount-subtract");
            int onlinePlayers = getProxy().getOnlineCount();
            int finalPlayerCount = (int) Math.round(onlinePlayers * playerCountMultiplier) - playerCountSubtract;

            ping.player_count = Math.max(0, finalPlayerCount);

            // Count Bedrock players by UUID format
            int bedrockCount = 0;
            for (ProxiedPlayer player : getProxy().getPlayers()) {
                if (player.getUniqueId().toString().startsWith("00000000-0000-0000")) {
                    bedrockCount++;
                }
            }

            ping.bedrock_player_count = bedrockCount;
            ping.java_player_count = ping.player_count - bedrockCount;

            api.insertServerPing(ping).thenRun(() -> {
                if (configManager.getBoolean("main", "debug")) {
                    getLogger().info("Server ping recorded successfully.");
                }
            });
        }, 0, 60, TimeUnit.SECONDS);
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