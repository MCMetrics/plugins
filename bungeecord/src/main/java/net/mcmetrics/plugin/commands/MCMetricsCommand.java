package net.mcmetrics.plugin.commands;

import net.mcmetrics.plugin.MCMetricsBungeePlugin;
import net.mcmetrics.plugin.SessionManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.mcmetrics.shared.MCMetricsAPI;
import net.mcmetrics.shared.models.CustomEvent;
import net.mcmetrics.shared.models.Payment;
import net.mcmetrics.shared.models.Session;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MCMetricsCommand extends Command {

    private final MCMetricsBungeePlugin plugin;
    private final String PRIMARY_COLOR = "#22c55f";

    public MCMetricsCommand(MCMetricsBungeePlugin plugin) {
        super("mcmetricsbungee", "mcmetrics.admin", "mcmetricsb", "mcmb");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcmetrics.admin")) {
            sender.sendMessage(new TextComponent(colorize("&cYou don't have permission to use this command.")));
            return;
        }

        if (args.length < 1) {
            sendHelpMessage(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "payment":
                handlePayment(sender, args);
                break;
            case "customevent":
                handleCustomEvent(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "setup":
                handleSetup(sender, args);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "info":
                handleInfo(sender, args);
                break;
            case "help":
                sendHelpMessage(sender);
                break;
            default:
                sender.sendMessage(new TextComponent(
                        colorize("&cUnknown subcommand. Use '/mcmetricsbungee help' for a list of commands.")));
                break;
        }
    }

    private void handlePayment(CommandSender sender, String[] args) {
        if (args.length != 6) {
            sender.sendMessage(new TextComponent(colorize(
                    "&cUsage: /mcmetricsbungee payment <platform> <player uuid/username> <transaction_id> <amount> <currency>")));
            return;
        }

        MCMetricsAPI api = plugin.getApi();
        if (api == null) {
            sender.sendMessage(new TextComponent(colorize(
                    "&cMCMetrics is not properly configured. Please use '/mcmetricsbungee setup' to configure the plugin.")));
            return;
        }

        UUID playerUuid;
        String playerName;

        try {
            playerUuid = UUID.fromString(args[2]);
            ProxiedPlayer player = plugin.getProxy().getPlayer(playerUuid);
            if (player != null) {
                playerName = player.getName();
            } else {
                playerName = args[2];
            }
        } catch (IllegalArgumentException e) {
            ProxiedPlayer player = plugin.getProxy().getPlayer(args[2]);
            if (player == null) {
                sender.sendMessage(new TextComponent(
                        colorize("&cPlayer not found. Note that for offline players, you must use their UUID.")));
                return;
            }
            playerUuid = player.getUniqueId();
            playerName = player.getName();
        }

        double amount;
        try {
            amount = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(new TextComponent(colorize("&cInvalid amount. Please enter a valid number.")));
            return;
        }

        final String finalPlayerName = playerName;
        Payment payment = new Payment();
        payment.platform = args[1];
        payment.player_uuid = playerUuid;
        payment.transaction_id = args[3];
        payment.amount = amount;
        payment.currency = args[5];
        payment.datetime = new Date();

        api.insertPayment(payment)
                .thenRun(() -> sender.sendMessage(new TextComponent(
                        colorize(PRIMARY_COLOR + "Payment recorded successfully for " + finalPlayerName + "."))))
                .exceptionally(e -> {
                    if (plugin.getConfigManager().getBoolean("main", "debug")) {
                        sender.sendMessage(
                                new TextComponent(colorize("&cFailed to record payment. Check console for details.")));
                    }
                    return null;
                });
    }

    private void handleCustomEvent(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(new TextComponent(colorize(
                    "&cUsage: /mcmetricsbungee customevent <player_uuid> <event_type> [key1=value1 key2=value2 ...]")));
            return;
        }

        MCMetricsAPI api = plugin.getApi();
        if (api == null) {
            sender.sendMessage(new TextComponent(colorize(
                    "&cMCMetrics is not properly configured. Please use '/mcmetricsbungee setup' to configure the plugin.")));
            return;
        }

        CustomEvent event = new CustomEvent();
        event.player_uuid = UUID.fromString(args[1]);
        event.event_type = args[2];
        event.timestamp = new Date();
        event.metadata = new HashMap<>();

        for (int i = 3; i < args.length; i++) {
            String[] keyValue = args[i].split("=");
            if (keyValue.length == 2) {
                event.metadata.put(keyValue[0], keyValue[1]);
            }
        }

        api.insertCustomEvent(event)
                .thenRun(() -> {
                    if (plugin.getConfigManager().getBoolean("main", "debug")) {
                        sender.sendMessage(
                                new TextComponent(colorize(PRIMARY_COLOR + "Custom event recorded successfully.")));
                    }
                    if (plugin.getConfigManager().getBoolean("main", "debug")) {
                        plugin.getLogger().info("Custom event recorded: " + event.event_type);
                    }
                })
                .exceptionally(e -> {
                    if (plugin.getConfigManager().getBoolean("main", "debug")) {
                        sender.sendMessage(
                                new TextComponent(
                                        colorize("&cFailed to record custom event. Check console for details.")));
                    }
                    return null;
                });
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadPlugin();
        sender.sendMessage(new TextComponent(colorize(PRIMARY_COLOR + "MCMetrics configuration reloaded.")));
    }

    private void handleSetup(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(new TextComponent(colorize("&cUsage: /mcmetricsbungee setup <server_id> <server_key>")));
            return;
        }

        try {
            plugin.getConfigManager().set("main", "server.id", args[1]);
            plugin.getConfigManager().set("main", "server.key", args[2]);
            plugin.getConfigManager().saveConfig("main");
            plugin.reloadPlugin();
            sender.sendMessage(
                    new TextComponent(colorize(PRIMARY_COLOR + "MCMetrics configuration updated and reloaded.")));
        } catch (IOException e) {
            sender.sendMessage(
                    new TextComponent(colorize("&cFailed to update configuration. Check console for details.")));
            plugin.getLogger().severe("Failed to update configuration: " + e.getMessage());
        }
    }

    private void handleStatus(CommandSender sender) {
        MCMetricsAPI api = plugin.getApi();
        if (api == null) {
            sender.sendMessage(new TextComponent(colorize(
                    "&cMCMetrics is not properly configured. Please use '/mcmetricsbungee setup' to configure the plugin.")));
            return;
        }

        sender.sendMessage(new TextComponent(colorize(PRIMARY_COLOR + "&lMCMetrics Status")));
        sender.sendMessage(new TextComponent(colorize("&7Plugin version: &f" + plugin.getDescription().getVersion())));
        sender.sendMessage(new TextComponent(colorize("&7Active sessions: &f" + plugin.getActiveSessionCount())));
        sender.sendMessage(new TextComponent(colorize("&7API requests in the last hour: &f" + api.getRequestCount())));
        sender.sendMessage(new TextComponent(colorize("&7API errors in the last hour: &f" + api.getErrorCount())));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(new TextComponent(colorize("&cUsage: /mcmetricsbungee info <player name or uuid>")));
            return;
        }

        ProxiedPlayer target = plugin.getProxy().getPlayer(args[1]);
        if (target == null) {
            try {
                UUID uuid = UUID.fromString(args[1]);
                target = plugin.getProxy().getPlayer(uuid);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (target == null) {
            sender.sendMessage(new TextComponent(colorize("&cPlayer not found or not online.")));
            return;
        }

        UUID playerId = target.getUniqueId();
        SessionManager sessionManager = plugin.getSessionManager();
        Session session = sessionManager.getSession(playerId);

        if (session == null) {
            sender.sendMessage(new TextComponent(colorize("&cNo active session found for this player.")));
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sender.sendMessage(new TextComponent(colorize(PRIMARY_COLOR + "&lPlayer Info: &f" + target.getName())));
        sender.sendMessage(new TextComponent(colorize("&7UUID: &f" + playerId)));
        sender.sendMessage(new TextComponent(colorize("&7Session start: &f" + sdf.format(session.session_start))));
        sender.sendMessage(new TextComponent(colorize("&7Domain: &f" + session.domain)));
        sender.sendMessage(new TextComponent(colorize("&7IP Address: &f" + session.ip_address)));

        Map<String, Integer> groupedEvents = sessionManager.getGroupedCustomEvents(playerId);
        sender.sendMessage(new TextComponent(colorize("&7Custom events this session:")));
        for (Map.Entry<String, Integer> entry : groupedEvents.entrySet()) {
            sender.sendMessage(
                    new TextComponent(colorize("  &f" + entry.getKey() + ": &7" + entry.getValue() + " times")));
        }

        sender.sendMessage(new TextComponent(colorize("&7Payments this session:")));
        for (Payment payment : sessionManager.getSessionPayments(playerId)) {
            sender.sendMessage(new TextComponent(
                    colorize("  &f" + payment.amount + " " + payment.currency + " &7(" + payment.platform + ")")));
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(new TextComponent(
                colorize(PRIMARY_COLOR + "&lMCMetrics Commands &7(v" + plugin.getDescription().getVersion() + ")")));
        sender.sendMessage(
                new TextComponent(colorize(PRIMARY_COLOR + "/mcmetricsbungee help &7- Show this help message")));
        sender.sendMessage(
                new TextComponent(colorize(PRIMARY_COLOR + "/mcmetricsbungee setup &f<server_id> <server_key>")));
        sender.sendMessage(new TextComponent(colorize("  &7- Configure the plugin")));
        sender.sendMessage(
                new TextComponent(colorize(PRIMARY_COLOR + "/mcmetricsbungee reload &7- Reload the configuration")));
        sender.sendMessage(
                new TextComponent(colorize(PRIMARY_COLOR + "/mcmetricsbungee status &7- Show plugin status")));
        sender.sendMessage(
                new TextComponent(colorize(PRIMARY_COLOR + "/mcmetricsbungee info &f<player name or uuid>")));
        sender.sendMessage(new TextComponent(colorize("  &7- Show player information")));
        sender.sendMessage(
                new TextComponent(colorize(PRIMARY_COLOR + "/mcmetricsbungee payment &f<platform> <player_uuid>")));
        sender.sendMessage(new TextComponent(colorize("  &f<transaction_id> <amount> <currency>")));
        sender.sendMessage(new TextComponent(colorize("  &7- Record a payment")));
        sender.sendMessage(new TextComponent(colorize(PRIMARY_COLOR + "/mcmetricsbungee customevent &f<player_uuid>")));
        sender.sendMessage(new TextComponent(colorize("  &f<event_type> [key1=value1 key2=value2 ...]")));
        sender.sendMessage(new TextComponent(colorize("  &7- Record a custom event")));
    }

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&',
                message.replace(PRIMARY_COLOR, ChatColor.of(PRIMARY_COLOR).toString()));
    }
}