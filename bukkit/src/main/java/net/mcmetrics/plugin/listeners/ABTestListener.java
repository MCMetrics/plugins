package net.mcmetrics.plugin.listeners;

import net.mcmetrics.plugin.MCMetricsSpigotPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class ABTestListener implements Listener {
    private final MCMetricsSpigotPlugin plugin;

    public ABTestListener(MCMetricsSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        boolean isFirstJoin = !event.getPlayer().hasPlayedBefore();
        plugin.getABTestManager().handleJoinTrigger(event.getPlayer(), isFirstJoin);
    }
}