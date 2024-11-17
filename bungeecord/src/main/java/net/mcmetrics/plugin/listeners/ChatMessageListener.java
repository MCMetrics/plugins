package net.mcmetrics.plugin.listeners;

import net.mcmetrics.plugin.MCMetricsBungeePlugin;
import net.mcmetrics.shared.models.ChatMessage;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class ChatMessageListener implements Listener {
    private final MCMetricsBungeePlugin plugin;

    public ChatMessageListener(MCMetricsBungeePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        if (event.isCancelled()) return;
        if (event.isCommand()) return; // Don't track commands
        if (!(event.getSender() instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.player_uuid = player.getUniqueId();
        chatMessage.player_username = player.getName();
        chatMessage.message = event.getMessage();

        plugin.getApi().insertChatMessage(chatMessage)
            .exceptionally(throwable -> {
                plugin.getLogger().warning("Failed to record chat message: " + throwable.getMessage());
                return null;
            });
    }
}