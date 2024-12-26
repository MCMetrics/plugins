package net.mcmetrics.plugin.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.mcmetrics.plugin.MCMetricsVelocityPlugin;
import net.mcmetrics.plugin.SessionManager;
import net.mcmetrics.shared.MCMetricsAPI;
import net.mcmetrics.shared.models.CustomEvent;
import net.mcmetrics.shared.models.Payment;
import net.mcmetrics.shared.models.Session;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class MCMetricsCommand implements SimpleCommand {

    private final MCMetricsVelocityPlugin plugin;
    private static final NamedTextColor PRIMARY_COLOR = NamedTextColor.GREEN;

    public MCMetricsCommand(MCMetricsVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean hasPermission(final Invocation invocation) {
        return invocation.source().hasPermission("mcmetrics.admin");
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission("mcmetrics.admin")) {
            source.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
            return;
        }

        if (args.length == 0) {
            sendHelpMessage(source);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "payment":
                if (args.length == 6) {
                    handlePayment(source, args[1], args[2], args[3], args[4], args[5]);
                } else {
                    source.sendMessage(Component.text("Usage: /mcmetricsvelocity payment <platform> <player_uuid> <transaction_id> <amount> <currency>").color(NamedTextColor.RED));
                }
                break;
            case "customevent":
                if (args.length >= 4) {
                    String metadata = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    handleCustomEvent(source, args[1], args[2], metadata);
                } else {
                    source.sendMessage(Component.text("Usage: /mcmetricsvelocity customevent <player_uuid> <event_type> [key1=value1 key2=value2 ...]").color(NamedTextColor.RED));
                }
                break;
            case "reload":
                handleReload(source);
                break;
            case "setup":
                if (args.length == 3) {
                    handleSetup(source, args[1], args[2]);
                } else {
                    source.sendMessage(Component.text("Usage: /mcmetricsvelocity setup <server_id> <server_key>").color(NamedTextColor.RED));
                }
                break;
            case "status":
                handleStatus(source);
                break;
            case "info":
                if (args.length == 2) {
                    handleInfo(source, args[1]);
                } else {
                    source.sendMessage(Component.text("Usage: /mcmetricsvelocity info <player>").color(NamedTextColor.RED));
                }
                break;
            case "help":
            default:
                sendHelpMessage(source);
                break;
        }
    }

    private void handlePayment(CommandSource source, String platform, String playerArg, String transactionId, String amount, String currency) {
        MCMetricsAPI api = plugin.getApi();
        if (api == null) {
            source.sendMessage(Component.text("MCMetrics is not properly configured. Please use '/mcmetricsvelocity setup' to configure the plugin.").color(NamedTextColor.RED));
            return;
        }

        UUID playerUuid;
        String playerName;

        try {
            playerUuid = UUID.fromString(playerArg);
            Optional<Player> player = plugin.getServer().getPlayer(playerUuid);
            if (player.isPresent()) {
                playerName = player.get().getUsername();
            } else {
                playerName = playerArg;
            }
        } catch (IllegalArgumentException e) {
            Optional<Player> player = plugin.getServer().getPlayer(playerArg);
            if (!player.isPresent()) {
                source.sendMessage(Component.text("Player not found. Note that for offline players, you must use their UUID.").color(NamedTextColor.RED));
                return;
            }
            playerUuid = player.get().getUniqueId();
            playerName = player.get().getUsername();
        }

        double parsedAmount;
        try {
            parsedAmount = Double.parseDouble(amount);
        } catch (NumberFormatException e) {
            source.sendMessage(Component.text("Invalid amount. Please enter a valid number.").color(NamedTextColor.RED));
            return;
        }

        final String finalPlayerName = playerName;
        Payment payment = new Payment();
        payment.platform = platform;
        payment.player_uuid = playerUuid;
        payment.transaction_id = transactionId;
        payment.amount = parsedAmount;
        payment.currency = currency;
        payment.datetime = new Date();

        api.insertPayment(payment)
                .thenRun(() -> source.sendMessage(Component.text("Payment recorded successfully for " + finalPlayerName + ".").color(PRIMARY_COLOR)))
                .exceptionally(e -> {
                    source.sendMessage(Component.text("Failed to record payment. Check console for details.").color(NamedTextColor.RED));
                    return null;
                });
    }

    private void handleCustomEvent(CommandSource source, String playerUuid, String eventType, String metadata) {
        MCMetricsAPI api = plugin.getApi();
        if (api == null) {
            source.sendMessage(Component.text("MCMetrics is not properly configured. Please use '/mcmetricsvelocity setup' to configure the plugin.").color(NamedTextColor.RED));
            return;
        }
    
        CustomEvent event = new CustomEvent();
        event.player_uuid = UUID.fromString(playerUuid);
        event.event_type = eventType;
        event.timestamp = new Date();
        event.metadata = new HashMap<>();
    
        String[] keyValuePairs = metadata.split(" ");
        for (String pair : keyValuePairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                event.metadata.put(keyValue[0], keyValue[1]);
            }
        }
    
        api.insertCustomEvent(event)
                .thenRun(() -> {
                    source.sendMessage(Component.text("Custom event recorded successfully.").color(PRIMARY_COLOR));
                    if (!source.equals(plugin.getServer().getConsoleCommandSource()) || 
                        plugin.getConfigManager().getBoolean("main", "debug")) {
                        plugin.getLogger().info("Custom event recorded: " + event.event_type);
                    }
                })
                .exceptionally(e -> {
                    source.sendMessage(Component.text("Failed to record custom event. Check console for details.").color(NamedTextColor.RED));
                    return null;
                });
    }

    private void handleReload(CommandSource source) {
        plugin.reloadPlugin();
        source.sendMessage(Component.text("MCMetrics configuration reloaded.").color(PRIMARY_COLOR));
    }

    private void handleSetup(CommandSource source, String serverId, String serverKey) {
        try {
            plugin.getConfigManager().set("main", "server.id", serverId);
            plugin.getConfigManager().set("main", "server.key", serverKey);
            plugin.getConfigManager().saveConfig("main");
            plugin.reloadPlugin();
            source.sendMessage(Component.text("MCMetrics configuration updated and reloaded.").color(PRIMARY_COLOR));
        } catch (IOException e) {
            source.sendMessage(Component.text("Failed to update configuration. Check console for details.").color(NamedTextColor.RED));
            plugin.getLogger().severe("Failed to update configuration: " + e.getMessage());
        }
    }

    private void handleStatus(CommandSource source) {
        MCMetricsAPI api = plugin.getApi();
        if (api == null) {
            source.sendMessage(Component.text("MCMetrics is not properly configured. Please use '/mcmetricsvelocity setup' to configure the plugin.").color(NamedTextColor.RED));
            return;
        }

        source.sendMessage(Component.text("MCMetrics Status").color(PRIMARY_COLOR).decorate(TextDecoration.BOLD));
        source.sendMessage(Component.text("Plugin version: ").color(NamedTextColor.GRAY).append(Component.text(plugin.getClass().getPackage().getImplementationVersion()).color(NamedTextColor.WHITE)));
        source.sendMessage(Component.text("Active sessions: ").color(NamedTextColor.GRAY).append(Component.text(plugin.getActiveSessionCount()).color(NamedTextColor.WHITE)));
        source.sendMessage(Component.text("API requests in the last hour: ").color(NamedTextColor.GRAY).append(Component.text(api.getRequestCount()).color(NamedTextColor.WHITE)));
        source.sendMessage(Component.text("API errors in the last hour: ").color(NamedTextColor.GRAY).append(Component.text(api.getErrorCount()).color(NamedTextColor.WHITE)));
    }

    private void handleInfo(CommandSource source, String playerName) {
        Optional<Player> targetOptional = plugin.getServer().getPlayer(playerName);
        if (!targetOptional.isPresent()) {
            source.sendMessage(Component.text("Player not found or not online.").color(NamedTextColor.RED));
            return;
        }

        Player target = targetOptional.get();
        UUID playerId = target.getUniqueId();
        SessionManager sessionManager = plugin.getSessionManager();
        Session session = sessionManager.getSession(playerId);

        if (session == null) {
            source.sendMessage(Component.text("No active session found for this player.").color(NamedTextColor.RED));
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        source.sendMessage(Component.text("Player Info: ").color(PRIMARY_COLOR).decorate(TextDecoration.BOLD).append(Component.text(target.getUsername()).color(NamedTextColor.WHITE)));
        source.sendMessage(Component.text("UUID: ").color(NamedTextColor.GRAY).append(Component.text(playerId.toString()).color(NamedTextColor.WHITE)));
        source.sendMessage(Component.text("Session start: ").color(NamedTextColor.GRAY).append(Component.text(sdf.format(session.session_start)).color(NamedTextColor.WHITE)));
        source.sendMessage(Component.text("Domain: ").color(NamedTextColor.GRAY).append(Component.text(session.domain).color(NamedTextColor.WHITE)));
        source.sendMessage(Component.text("IP Address: ").color(NamedTextColor.GRAY).append(Component.text(session.ip_address).color(NamedTextColor.WHITE)));

        Map<String, Integer> groupedEvents = sessionManager.getGroupedCustomEvents(playerId);
        source.sendMessage(Component.text("Custom events this session:").color(NamedTextColor.GRAY));
        for (Map.Entry<String, Integer> entry : groupedEvents.entrySet()) {
            source.sendMessage(Component.text("  " + entry.getKey() + ": ").color(NamedTextColor.WHITE).append(Component.text(entry.getValue() + " times").color(NamedTextColor.GRAY)));
        }

        source.sendMessage(Component.text("Payments this session:").color(NamedTextColor.GRAY));
        for (Payment payment : sessionManager.getSessionPayments(playerId)) {
            source.sendMessage(Component.text("  " + payment.amount + " " + payment.currency + " ").color(NamedTextColor.WHITE).append(Component.text("(" + payment.platform + ")").color(NamedTextColor.GRAY)));
        }
    }

    private void sendHelpMessage(CommandSource source) {
        source.sendMessage(Component.text("MCMetrics Commands ").color(PRIMARY_COLOR).decorate(TextDecoration.BOLD).append(Component.text("(v" + plugin.getClass().getPackage().getImplementationVersion() + ")").color(NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/mcmetricsvelocity help").color(PRIMARY_COLOR).append(Component.text(" - Show this help message").color(NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/mcmetricsvelocity setup <server_id> <server_key>").color(PRIMARY_COLOR).append(Component.text(" - Configure the plugin").color(NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/mcmetricsvelocity reload").color(PRIMARY_COLOR).append(Component.text(" - Reload the configuration").color(NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/mcmetricsvelocity status").color(PRIMARY_COLOR).append(Component.text(" - Show plugin status").color(NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/mcmetricsvelocity info <player name or uuid>").color(PRIMARY_COLOR).append(Component.text(" - Show player information").color(NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/mcmetricsvelocity payment <platform> <player_uuid> <transaction_id> <amount> <currency>").color(PRIMARY_COLOR).append(Component.text(" - Record a payment").color(NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/mcmetricsvelocity customevent <player_uuid> <event_type> [key1=value1 key2=value2 ...]").color(PRIMARY_COLOR).append(Component.text(" - Record a custom event").color(NamedTextColor.GRAY)));
    }
}