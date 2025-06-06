package net.mcmetrics.plugin.listeners;

import net.mcmetrics.plugin.MCMetricsBungeePlugin;
import net.mcmetrics.plugin.SessionManager;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.mcmetrics.shared.models.Session;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class PlayerSessionListener implements Listener {

    private final MCMetricsBungeePlugin plugin;
    private final SessionManager sessionManager;

    public PlayerSessionListener(MCMetricsBungeePlugin plugin, SessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    @EventHandler
    public void onPlayerLogin(LoginEvent event) {
        UUID playerId = event.getConnection().getUniqueId();

        if (isExempt(playerId))
            return;

        Session session = new Session();
        session.player_uuid = playerId;
        session.player_username = event.getConnection().getName();
        session.session_start = new Date();
        session.ip_address = event.getConnection().getAddress().getAddress().getHostAddress();
        session.domain = event.getConnection().getVirtualHost().getHostName();

        sessionManager.startSession(playerId, session);
    }

    @EventHandler
    public void onPlayerJoin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (isExempt(playerId))
            return;

        Session session = sessionManager.getSession(playerId);
        if (session == null) {
            plugin.getLogger().warning("Player joined, but could not find session: " + playerId);
            return;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (isExempt(playerId))
            return;

        Session session = sessionManager.endSession(playerId);
        if (session == null) {
            plugin.getLogger().warning("Player quit, but could not find session: " + playerId);
            return;
        }

        session.session_end = new Date();

        plugin.getApi().insertSession(session)
                .thenRun(() -> {
                    if (plugin.getConfigManager().getBoolean("main", "debug")) {
                        plugin.getLogger().info("Session uploaded for player: " + playerId);
                    }
                })
                .exceptionally(e -> {
                    // Use rate-limited logging for session upload errors
                    plugin.getApi().logErrorWithRateLimit("SESSION_UPLOAD_ERROR",
                            "Failed to upload session for player " + playerId + ": " + e.getMessage());
                    return null;
                });
    }

    private boolean isExempt(UUID playerId) {
        try {
            List<?> exemptPlayers = plugin.getConfigManager().getList("main", "exempt.players");
            return exemptPlayers != null && (exemptPlayers.contains(playerId.toString()));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check exempt status for player " + playerId + ": " + e.getMessage());
            return false;
        }
    }
}