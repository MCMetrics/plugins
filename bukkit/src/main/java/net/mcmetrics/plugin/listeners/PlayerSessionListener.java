package net.mcmetrics.plugin.listeners;

import net.mcmetrics.plugin.MCMetricsSpigotPlugin;
import net.mcmetrics.plugin.SessionManager;
import net.mcmetrics.shared.models.Session;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class PlayerSessionListener implements Listener {

    private final MCMetricsSpigotPlugin plugin;
    private final SessionManager sessionManager;

    public PlayerSessionListener(MCMetricsSpigotPlugin plugin, SessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }

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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (isExempt(player)) return;

        Session session = sessionManager.getSession(playerId);
        if (session == null) {
            plugin.getLogger().warning("Player joined, but could not find session: " + playerId);
            return;
        }

        // Check for potential legacy players during join event when hasPlayedBefore() is reliable
        if (player.hasPlayedBefore() && !plugin.getLegacyPlayerManager().isKnownPlayer(playerId)) {
            session.potential_legacy = true;
            // plugin.getLogger().info("Marked player " + player.getName() + " as potential legacy player");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
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
            .thenAccept(v -> {
                plugin.getLogger().info("Session uploaded for player: " + playerId);
                // Only add to known players if this was a potential legacy player and upload succeeded
                if (session.potential_legacy) {
                    plugin.getLegacyPlayerManager().addKnownPlayer(playerId);
                    // plugin.getLogger().info("Added " + player.getName() + " to known players list after successful session upload");
                }
            })
            .exceptionally(e -> {
                plugin.getLogger().severe("Failed to upload session for player " + playerId + ": " + e.getMessage());
                return null;
            });
    }

    private boolean isExempt(Player player) {
        List<?> exemptPlayers = plugin.getConfigManager().getList("main", "exempt.players");
        return exemptPlayers != null && (
            exemptPlayers.contains(player.getName()) ||
            exemptPlayers.contains(player.getUniqueId().toString())
        );
    }
}