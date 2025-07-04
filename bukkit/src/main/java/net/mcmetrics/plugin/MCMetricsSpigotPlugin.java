package net.mcmetrics.plugin;

import com.tcoded.folialib.FoliaLib;
import net.mcmetrics.plugin.commands.MCMetricsCommand;
import net.mcmetrics.plugin.listeners.*;
import net.mcmetrics.shared.MCMetricsAPI;
import net.mcmetrics.shared.config.ConfigManager;
import net.mcmetrics.shared.models.ServerPing;
import net.mcmetrics.shared.models.Session;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MCMetricsSpigotPlugin extends JavaPlugin {
    private MCMetricsAPI api;
    private SessionManager sessionManager;
    private ConfigManager configManager;
    private LegacyPlayerManager legacyPlayerManager;
    private ABTestManager abTestManager;
    private ConsoleEventListener consoleEventListener;
    private FoliaLib foliaLib;

    @Override
    public void onEnable() {
        // Initialize FoliaLib
        foliaLib = new FoliaLib(this);

        configManager = new ConfigManager();
        try {
            configManager.loadConfig("main", getDataFolder(), "config.yml",
                    getClass().getResourceAsStream("/config.yml"), getLogger());
        } catch (IOException e) {
            getLogger().severe("Failed to load configuration: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        legacyPlayerManager = new LegacyPlayerManager(getDataFolder(), getLogger());

        initializeAPI();
        sessionManager = new SessionManager(api);
        abTestManager = new ABTestManager(this, api, getLogger());

        // event listeners
        getServer().getPluginManager().registerEvents(new PlayerSessionListener(this, sessionManager), this);
        getServer().getPluginManager().registerEvents(new ABTestListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatMessageListener(this), this);

        // Only setup console event listener on non-Folia servers and if not disabled in
        // config
        if (!foliaLib.isFolia() && !configManager.getBoolean("main", "disable-console-listener")) {
            consoleEventListener = new ConsoleEventListener(this);
            getServer().getPluginManager().registerEvents(consoleEventListener, this);
            getLogger().info("Initialized console event listener");
        } else {
            if (foliaLib.isFolia()) {
                getLogger().info("Console event listener disabled on Folia server");
            } else {
                getLogger().info("Console event listener disabled by configuration");
            }
        }

        // commands
        getCommand("mcmetrics").setExecutor(new MCMetricsCommand(this));

        fetchABTests();

        startServerPingTask();

        getLogger().info("MCMetrics plugin has been enabled. Thank you for using MCMetrics!");
    }

    private void fetchABTests() {
        if (api == null) {
            getLogger().warning("Cannot fetch A/B tests: API not initialized");
            return;
        }
        abTestManager.fetchTests();
    }

    public void initializeAPI() {
        String serverId = configManager.getString("main", "server.id");
        String serverKey = configManager.getString("main", "server.key");

        if (serverId == null || serverKey == null || serverId.isEmpty() || serverKey.isEmpty()) {
            getLogger().warning(
                    "Server ID or Server Key not set in config.yml. Please use /mcmetrics setup to configure the plugin.");
            return;
        }

        api = new MCMetricsAPI(serverId, serverKey, getLogger());
    }

    @Override
    public void onDisable() {
        // Cancel all tasks registered with FoliaLib
        if (foliaLib != null) {
            foliaLib.getScheduler().cancelAllTasks();
        }

        if (sessionManager != null) {
            List<Session> remainingSessions = sessionManager.endAllSessions();
            if (!remainingSessions.isEmpty()) {
                getLogger().info("Ending " + remainingSessions.size() + " remaining sessions...");

                // Set end time for all sessions
                Date endTime = new Date();
                for (Session session : remainingSessions) {
                    session.session_end = endTime;
                }

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

        if (consoleEventListener != null) {
            consoleEventListener.shutdown();
            getLogger().info("Console event listener shut down");
        }

        if (api != null) {
            api.shutdown();
        }
        getLogger().info("MCMetrics plugin disabled.");
    }

    private void startServerPingTask() {
        // Using FoliaLib instead of BukkitRunnable
        foliaLib.getScheduler().runTimerAsync(() -> {
            if (api == null) {
                getLogger().warning(
                        "MCMetrics API is not initialized. Please use /mcmetrics setup to configure the plugin.");
                return;
            }

            ServerPing ping = new ServerPing();
            ping.time = new Date();

            double playerCountMultiplier = configManager.getDouble("main", "playercount-multiplier");
            int playerCountSubtract = configManager.getInt("main", "playercount-subtract");
            int onlinePlayers = getServer().getOnlinePlayers().size();
            int finalPlayerCount = (int) Math.round(onlinePlayers * playerCountMultiplier) - playerCountSubtract;

            ping.player_count = Math.max(0, finalPlayerCount);
            ping.bedrock_player_count = (int) getServer().getOnlinePlayers().stream()
                    .map(player -> player.getUniqueId().toString())
                    .filter(uuid -> uuid.startsWith("00000000-0000-0000"))
                    .count();
            ping.java_player_count = ping.player_count - ping.bedrock_player_count;

            api.insertServerPing(ping).thenRun(() -> {
                if (isDebug()) {
                    if (!isSilent()) {
                        getLogger().info("Server ping recorded successfully.");
                    }
                }
            });
        }, 0, 10 * 20); // Run every 10 seconds (200 ticks)
    }

    public MCMetricsAPI getApi() {
        return api;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LegacyPlayerManager getLegacyPlayerManager() {
        return legacyPlayerManager;
    }

    public ABTestManager getABTestManager() {
        return abTestManager;
    }

    public ConsoleEventListener getConsoleEventListener() {
        return consoleEventListener;
    }

    public FoliaLib getFoliaLib() {
        return foliaLib;
    }

    public void reloadPlugin() {
        try {
            configManager.reloadConfig("main");
            initializeAPI();
            fetchABTests();
            if (consoleEventListener != null) {
                consoleEventListener.loadEventPatterns();
            }
            getLogger().info("MCMetrics configuration reloaded.");
        } catch (IOException e) {
            getLogger().severe("Failed to reload configuration: " + e.getMessage());
        }
    }

    public boolean isDebug() {
        String logLevel = configManager.getString("main", "log-level");
        return logLevel != null && logLevel.equalsIgnoreCase("debug");
    }

    public boolean isSilent() {
        String logLevel = configManager.getString("main", "log-level");
        return logLevel != null && logLevel.equalsIgnoreCase("silent");
    }

    public int getActiveSessionCount() {
        return sessionManager.getActiveSessionCount();
    }

    public boolean isBedrockPlayer(Player player) {
        return player.getUniqueId().toString().startsWith("00000000-0000-0000");
    }

    public long getAFKTime(UUID playerId) {
        // TODO: Implement AFK time tracking
        return 0;
    }
}