package net.mcmetrics.plugin.listeners;

import net.mcmetrics.plugin.MCMetricsSpigotPlugin;
import net.mcmetrics.plugin.SessionManager;
import net.mcmetrics.sdk.models.Session;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Date;
import java.util.UUID;

public class PlayerSessionListener implements Listener {

    private final MCMetricsSpigotPlugin plugin;
    private final SessionManager sessionManager;

    public PlayerSessionListener(MCMetricsSpigotPlugin plugin, SessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (isExempt(player)) return;

        Session session = new Session();
        session.player_uuid = playerId;
        session.player_username = player.getName();
        session.session_start = new Date();
        session.ip_address = event.getAddress().getHostAddress();
        session.domain = event.getHostname();

        sessionManager.startSession(playerId, session);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (isExempt(player)) return;

        Session session = sessionManager.getSession(playerId);
        if (session == null) {
            plugin.getLogger().warning("Player joined, but could not find session: " + playerId);
            return;
        }

        // You might want to add more data to the session here if needed
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (isExempt(player)) return;

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

    private boolean isExempt(Player player) {
        return plugin.getConfig().getStringList("exempt.players").contains(player.getName()) ||
                plugin.getConfig().getStringList("exempt.players").contains(player.getUniqueId().toString());
    }
}