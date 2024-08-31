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
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class MCMetricsSpigotPlugin extends JavaPlugin {
    private MCMetricsAPI api;
    private SessionManager sessionManager;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize API
        String serverId = getConfig().getString("server.id");
        String serverKey = getConfig().getString("server.key");
        Logger logger = getLogger();

        if (serverId == null || serverKey == null) {
            logger.severe("Server ID or Server Key not set in config.yml. Plugin disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        api = new MCMetricsAPI(serverId, serverKey, logger);
        sessionManager = new SessionManager(api);

        // Register event listeners
        getServer().getPluginManager().registerEvents(new PlayerSessionListener(this, sessionManager), this);

        // Register commands
        getCommand("mcmetrics").setExecutor(new MCMetricsCommand(this, api));

        // Start server ping task
        startServerPingTask();

        logger.info("MCMetrics plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MCMetrics plugin is being disabled. Uploading remaining sessions...");

        List<Session> remainingSessions = sessionManager.endAllSessions();
        CompletableFuture<Void>[] uploadFutures = new CompletableFuture[remainingSessions.size()];

        for (int i = 0; i < remainingSessions.size(); i++) {
            Session session = remainingSessions.get(i);
            session.session_end = new Date(); // players are getting kicked, so end the session now

            uploadFutures[i] = api.insertSession(session)
                    .thenRun(() -> getLogger().info("Session uploaded for player: " + session.player_uuid))
                    .exceptionally(e -> {
                        getLogger().severe("Failed to upload session for player " + session.player_uuid + ": " + e.getMessage());
                        return null;
                    });
        }

        // Wait for all uploads to complete
        CompletableFuture.allOf(uploadFutures).join();

        getLogger().info("All remaining sessions have been uploaded. MCMetrics plugin disabled.");
    }

    private void startServerPingTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                ServerPing ping = new ServerPing();
                ping.time = new Date();
                ping.player_count = getServer().getOnlinePlayers().size();
                ping.java_player_count = ping.player_count; // Assuming all are Java players
                ping.bedrock_player_count = 0; // Assuming no Bedrock players
                ping.tps = 20.0; //TODO:
                ping.cpu_percent = -1; //TODO:
                ping.ram_percent = 0.0; //TODO:
                ping.entities_loaded = 0; //TODO;
                ping.chunks_loaded = 0; //TODO;

                api.insertServerPing(ping)
                        .thenRun(() -> getLogger().info("Server ping sent successfully"))
                        .exceptionally(e -> {
                            getLogger().severe("Failed to send server ping: " + e.getMessage());
                            return null;
                        });
            }
        }.runTaskTimerAsynchronously(this, 0L, 20L * 10); // Run every 10 seconds
    }

    public MCMetricsAPI getApi() {
        return api;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }
}