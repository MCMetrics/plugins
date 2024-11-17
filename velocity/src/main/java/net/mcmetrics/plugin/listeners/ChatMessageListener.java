package net.mcmetrics.plugin.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import net.mcmetrics.plugin.MCMetricsVelocityPlugin;
import net.mcmetrics.shared.models.ChatMessage;

public class ChatMessageListener {
    private final MCMetricsVelocityPlugin plugin;

    public ChatMessageListener(MCMetricsVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.player_uuid = event.getPlayer().getUniqueId();
        chatMessage.player_username = event.getPlayer().getUsername();
        chatMessage.message = event.getMessage();

        plugin.getApi().insertChatMessage(chatMessage)
            .exceptionally(throwable -> {
                plugin.getLogger().warning("Failed to record chat message: " + throwable.getMessage());
                return null;
            });
    }
}