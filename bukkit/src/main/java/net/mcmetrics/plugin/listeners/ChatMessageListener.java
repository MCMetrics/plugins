package net.mcmetrics.plugin.listeners;

import net.mcmetrics.plugin.MCMetricsSpigotPlugin;
import net.mcmetrics.shared.models.ChatMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatMessageListener implements Listener {
    private final MCMetricsSpigotPlugin plugin;

    public ChatMessageListener(MCMetricsSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) return;
        
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.player_uuid = event.getPlayer().getUniqueId();
        chatMessage.player_username = event.getPlayer().getName();
        chatMessage.message = event.getMessage();

        plugin.getApi().insertChatMessage(chatMessage)
            .exceptionally(throwable -> {
                plugin.getLogger().warning("Failed to record chat message: " + throwable.getMessage());
                return null;
            });
    }
}