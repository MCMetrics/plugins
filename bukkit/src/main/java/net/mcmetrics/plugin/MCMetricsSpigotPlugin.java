package net.mcmetrics.plugin;

import net.mcmetrics.plugin.commands.MCMetricsCommand;
import net.mcmetrics.plugin.listeners.PlayerSessionListener;
import net.mcmetrics.shared.MCMetricsAPI;
import net.mcmetrics.shared.models.ServerPing;
import net.mcmetrics.shared.models.Session;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class MCMetricsSpigotPlugin extends JavaPlugin {
    private MCMetricsAPI api;
    private SessionManager sessionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initializeAPI();
        getServer().getPluginManager().registerEvents(new PlayerSessionListener(this, sessionManager), this);
        getCommand("mcmetrics").setExecutor(new MCMetricsCommand(this));
        startServerPingTask();
        getLogger().info("MCMetrics plugin enabled successfully!");
    }

    public void initializeAPI() {
        String serverId = getConfig().getString("server.id");
        String serverKey = getConfig().getString("server.key");
        Logger logger = getLogger();

        if (serverId == null || serverKey == null || serverId.isEmpty() || serverKey.isEmpty()) {
            logger.warning("Server ID or Server Key not set in config.yml. Please use /mcmetrics setup to configure the plugin.");
            return;
        }

        api = new MCMetricsAPI(serverId, serverKey, logger);
        sessionManager = new SessionManager(api);
    }

    @Override
    public void onDisable() {
        if (sessionManager != null) {
            List<Session> remainingSessions = sessionManager.endAllSessions();
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
                // we get the bedrock player count by looping through each online player uuid. If it's  LIKE '00000000-0000-0000%', it's a bedrock player
                ping.bedrock_player_count = (int) getServer().getOnlinePlayers().stream().map(player -> player.getUniqueId().toString()).filter(uuid -> uuid.startsWith("00000000-0000-0000")).count();
                ping.java_player_count = ping.player_count - ping.bedrock_player_count;
                ping.tps = 20.0; //TODO: Implement actual TPS calculation
                ping.mspt = 0.0; //TODO: Implement actual MSPT calculation
                ping.cpu_percent = 0.0; //TODO: Implement CPU usage calculation
                ping.ram_percent = 0.0; //TODO: Implement RAM usage calculation
                ping.entities_loaded = 0;
                ping.chunks_loaded = 0;

                api.insertServerPing(ping).thenRun(() -> getLogger().info("Server ping recorded successfully."));
            }
        }.runTaskTimerAsynchronously(this, 0L, 20L * 10); // Run every 10 seconds
    }

    public MCMetricsAPI getApi() {
        return api;
    }

    public void reloadPlugin() {
        reloadConfig();
        initializeAPI();
        getLogger().info("MCMetrics configuration reloaded.");
    }
}