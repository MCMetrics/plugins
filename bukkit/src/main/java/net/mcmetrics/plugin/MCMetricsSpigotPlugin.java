package net.mcmetrics.plugin;

import net.mcmetrics.plugin.commands.MCMetricsCommand;
import net.mcmetrics.plugin.listeners.PlayerSessionListener;
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

public class MCMetricsSpigotPlugin extends JavaPlugin {
    private MCMetricsAPI api;
    private SessionManager sessionManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        // Initialize config manager
        configManager = new ConfigManager();
        try {
            configManager.loadConfig("main", getDataFolder(), "config.yml", 
                getClass().getResourceAsStream("/config.yml"), getLogger());
        } catch (IOException e) {
            getLogger().severe("Failed to load configuration: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        initializeAPI();
        sessionManager = new SessionManager(api);
        getServer().getPluginManager().registerEvents(new PlayerSessionListener(this, sessionManager), this);
        getCommand("mcmetrics").setExecutor(new MCMetricsCommand(this));
        startServerPingTask();
        getLogger().info("MCMetrics plugin enabled successfully!");
    }

    public void initializeAPI() {
        String serverId = configManager.getString("main", "server.id");
        String serverKey = configManager.getString("main", "server.key");

        if (serverId == null || serverKey == null || serverId.isEmpty() || serverKey.isEmpty()) {
            getLogger().warning("Server ID or Server Key not set in config.yml. Please use /mcmetrics setup to configure the plugin.");
            return;
        }

        api = new MCMetricsAPI(serverId, serverKey, getLogger());
    }

    @Override
    public void onDisable() {
        if (sessionManager != null) {
            List<Session> remainingSessions = sessionManager.endAllSessions();
            getLogger().info("Ending " + remainingSessions.size() + " remaining sessions...");
            for (Session session : remainingSessions) {
                session.session_end = new Date();
                api.insertSession(session).join(); // Wait for each session to be inserted
            }
        }
        getLogger().info("MCMetrics plugin disabled.");
    }

    private void startServerPingTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (api == null) {
                    getLogger().warning("MCMetrics API is not initialized. Please use /mcmetrics setup to configure the plugin.");
                    return;
                }

                ServerPing ping = new ServerPing();
                ping.time = new Date();
                ping.player_count = getServer().getOnlinePlayers().size();
                ping.bedrock_player_count = (int) getServer().getOnlinePlayers().stream()
                    .map(player -> player.getUniqueId().toString())
                    .filter(uuid -> uuid.startsWith("00000000-0000-0000"))
                    .count();
                ping.java_player_count = ping.player_count - ping.bedrock_player_count;

                api.insertServerPing(ping).thenRun(() -> 
                    getLogger().info("Server ping recorded successfully."));
            }
        }.runTaskTimerAsynchronously(this, 0L, 60L * 10); // Run every 60 seconds
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

    public boolean isBedrockPlayer(Player player) {
        return player.getUniqueId().toString().startsWith("00000000-0000-0000");
    }

    public long getAFKTime(UUID playerId) {
        // TODO: Implement AFK time tracking
        return 0;
    }
}
