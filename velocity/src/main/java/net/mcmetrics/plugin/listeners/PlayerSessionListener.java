package net.mcmetrics.plugin.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.mcmetrics.plugin.MCMetricsVelocityPlugin;
import net.mcmetrics.plugin.SessionManager;
import net.mcmetrics.shared.models.Session;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.UUID;

public class PlayerSessionListener {

    private final MCMetricsVelocityPlugin plugin;
    private final SessionManager sessionManager;

    public PlayerSessionListener(MCMetricsVelocityPlugin plugin, SessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        if (isExempt(playerId)) return;

        Session session = new Session();
        session.player_uuid = playerId;
        session.player_username = event.getPlayer().getUsername();
        session.session_start = new Date();
        InetSocketAddress address = event.getPlayer().getRemoteAddress();
        session.ip_address = address != null ? address.getAddress().getHostAddress() : "unknown";
        session.domain = event.getPlayer().getVirtualHost().map(vh -> vh.getHostString()).orElse("unknown");

        sessionManager.startSession(playerId, session);
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (isExempt(playerId)) return;

        Session session = sessionManager.getSession(playerId);
        if (session == null) {
            plugin.getLogger().warning("Player joined, but could not find session: " + playerId);
            return;
        }

    }

    @Subscribe
    public void onPlayerQuit(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (isExempt(playerId)) return;

        Session session = sessionManager.endSession(playerId);
        if (session == null) {
            plugin.getLogger().warning("Player quit, but could not find session: " + playerId);
            return;
        }

        session.session_end = new Date();

        plugin.getApi().insertSession(session)
                .thenRun(() -> plugin.getLogger().info("Session uploaded for player: " + playerId))
                .exceptionally(e -> {
                    plugin.getLogger().severe("Failed to upload session for player " + playerId + ": " + e.getMessage());
                    return null;
                });
    }

    private boolean isExempt(UUID playerId) {
        try {
            return plugin.getConfigManager().getList("main", "exempt.players").contains(playerId.toString());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check exempt status for player " + playerId + ": " + e.getMessage());
            return false;
        }
    }
}