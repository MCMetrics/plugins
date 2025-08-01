package net.mcmetrics.plugin.commands;

import net.mcmetrics.plugin.MCMetricsSpigotPlugin;
import net.mcmetrics.plugin.SessionManager;
import net.mcmetrics.plugin.listeners.ConsoleEventListener;
import net.mcmetrics.shared.MCMetricsAPI;
import net.mcmetrics.shared.models.ABTest;
import net.mcmetrics.shared.models.CustomEvent;
import net.mcmetrics.shared.models.Payment;
import net.mcmetrics.shared.models.Session;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

import net.mcmetrics.plugin.listeners.ConsoleEventListener.EventPatternInfo;

public class MCMetricsCommand implements CommandExecutor {

    private final MCMetricsSpigotPlugin plugin;
    private final String PRIMARY_COLOR = "#22c55f";

    public MCMetricsCommand(MCMetricsSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mcmetrics.admin")) {
            sender.sendMessage(colorize("&cYou don't have permission to use this command."));
            return true;
        }

        if (args.length < 1) {
            sendHelpMessage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "payment":
                return handlePayment(sender, args);
            case "customevent":
                return handleCustomEvent(sender, args);
            case "customevents":
                return handleCustomEvents(sender);
            case "reload":
                return handleReload(sender);
            case "setup":
                return handleSetup(sender, args);
            case "status":
                return handleStatus(sender);
            case "info":
                return handleInfo(sender, args);
            case "abtests":
                return handleABTests(sender);
            case "abtest":
                return handleABTest(sender, args);
            case "ignore":
                return handleIgnore(sender, args);
            case "help":
                sendHelpMessage(sender);
                return true;
            default:
                sender.sendMessage(colorize("&cUnknown subcommand. Use '/mcmetrics help' for a list of commands."));
                return true;
        }
    }

    private boolean handlePayment(CommandSender sender, String[] args) {
        if (args.length != 6) {
            sender.sendMessage(colorize(
                    "&cUsage: /mcmetrics payment <platform> <player username/uuid> <transaction_id> <amount> <currency>"));
            return true;
        }

        MCMetricsAPI api = plugin.getApi();
        if (api == null) {
            sender.sendMessage(colorize(
                    "&cMCMetrics is not properly configured. Please use '/mcmetrics setup' to configure the plugin."));
            return true;
        }

        UUID playerUuid;
        String playerName;

        // Parse player argument
        try {
            playerUuid = UUID.fromString(args[2]);
            Player player = Bukkit.getPlayer(playerUuid);
            playerName = player != null ? player.getName() : args[2];
        } catch (IllegalArgumentException e) {
            Player player = Bukkit.getPlayer(args[2]);
            if (player == null) {
                sender.sendMessage(
                        colorize("&cPlayer not found. Note that for offline players, you must use their UUID."));
                return true;
            }
            playerUuid = player.getUniqueId();
            playerName = player.getName();
        }

        double amount;
        try {
            amount = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(colorize("&cInvalid amount. Please enter a valid number."));
            return true;
        }

        final String finalPlayerName = playerName;
        final UUID finalPlayerId = playerUuid;

        Payment payment = new Payment();
        payment.platform = args[1];
        payment.player_uuid = playerUuid;
        payment.transaction_id = args[3];
        payment.amount = amount;
        payment.currency = args[5];
        payment.datetime = new Date();

        api.insertPayment(payment)
                .thenRun(() -> {
                    sender.sendMessage(
                            colorize(PRIMARY_COLOR + "Payment recorded successfully for " + finalPlayerName + "."));
                    Player player = Bukkit.getPlayer(finalPlayerId);
                    if (player != null && player.isOnline()) {
                        plugin.getABTestManager().handlePurchaseTrigger(player);
                    }
                })
                .exceptionally(e -> {
                    if (plugin.getConfigManager().getBoolean("main", "debug")) {
                        sender.sendMessage(colorize("&cFailed to record payment. Check console for details."));
                    }
                    return null;
                });

        return true;
    }

    private boolean handleCustomEvent(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(colorize(
                    "&cUsage: /mcmetrics customevent <player_uuid/name> <event_type> [key1=value1 key2=value2 ...]"));
            return true;
        }

        MCMetricsAPI api = plugin.getApi();
        if (api == null) {
            sender.sendMessage(colorize(
                    "&cMCMetrics is not properly configured. Please use '/mcmetrics setup' to configure the plugin."));
            return true;
        }

        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            Player player = Bukkit.getPlayer(args[1]);
            if (player == null) {
                sender.sendMessage(
                        colorize("&cPlayer not found. Note that for offline players, you must use their UUID."));
                return true;
            }
            playerUuid = player.getUniqueId();
        }

        CustomEvent event = new CustomEvent();
        event.player_uuid = playerUuid;
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
                    if (plugin.isDebug()) {
                        sender.sendMessage(colorize(PRIMARY_COLOR + "Custom event recorded successfully."));
                    }
                    if (!(sender instanceof ConsoleCommandSender)
                            || plugin.isDebug()) {
                        if (!plugin.isSilent()) {
                            plugin.getLogger().info("Custom event recorded: " + event.event_type);
                        }
                    }
                })
                .exceptionally(e -> {
                    if (plugin.isDebug()) {
                        sender.sendMessage(colorize("&cFailed to record custom event. Check console for details."));
                    }
                    return null;
                });

        return true;
    }

    private boolean handleCustomEvents(CommandSender sender) {
        MCMetricsAPI api = plugin.getApi();
        if (api == null) {
            sender.sendMessage(colorize(
                    "&cMCMetrics is not properly configured. Please use '/mcmetrics setup' to configure the plugin."));
            return true;
        }

        sender.sendMessage(colorize(PRIMARY_COLOR + "&l📊 Active Custom Event Listeners"));

        ConsoleEventListener consoleEventListener = plugin.getConsoleEventListener();
        if (consoleEventListener == null || consoleEventListener.getEventPatternsInfo().isEmpty()) {
            sender.sendMessage(colorize("&7No custom event listeners configured."));
            return true;
        }

        sender.sendMessage(colorize("&7Console message patterns:"));
        for (EventPatternInfo pattern : consoleEventListener.getEventPatternsInfo()) {
            sender.sendMessage(colorize("&8&m                                                "));
            sender.sendMessage(colorize(PRIMARY_COLOR + "Name: &f" + pattern.getName()));
            sender.sendMessage(colorize(PRIMARY_COLOR + "Pattern: &7" + pattern.getPattern()));
            sender.sendMessage(colorize(PRIMARY_COLOR + "Player field: &7" + pattern.getPlayerField()));
        }

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadPlugin();
        sender.sendMessage(colorize(PRIMARY_COLOR + "MCMetrics configuration reloaded."));
        return true;
    }

    private boolean handleSetup(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(colorize("&cUsage: /mcmetrics setup <server_id> <server_key>"));
            return true;
        }

        try {
            plugin.getConfigManager().set("main", "server.id", args[1]);
            plugin.getConfigManager().set("main", "server.key", args[2]);
            plugin.getConfigManager().saveConfig("main");
            plugin.reloadPlugin();
            sender.sendMessage(colorize(PRIMARY_COLOR + "MCMetrics configuration updated and reloaded."));
        } catch (IOException e) {
            sender.sendMessage(colorize("&cFailed to update configuration. Check console for details."));
            plugin.getLogger().severe("Failed to update configuration: " + e.getMessage());
        }

        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        MCMetricsAPI api = plugin.getApi();
        if (api == null) {
            sender.sendMessage(colorize(
                    "&cMCMetrics is not properly configured. Please use '/mcmetrics setup' to configure the plugin."));
            return true;
        }

        sender.sendMessage(colorize(PRIMARY_COLOR + "&lMCMetrics Status"));
        sender.sendMessage(colorize("&7Plugin version: &f" + plugin.getDescription().getVersion()));
        sender.sendMessage(colorize("&7Active sessions: &f" + plugin.getActiveSessionCount()));
        sender.sendMessage(colorize("&7API requests in the last hour: &f" + api.getRequestCount()));
        sender.sendMessage(colorize("&7API errors in the last hour: &f" + api.getErrorCount()));

        return true;
    }

    private boolean handleABTests(CommandSender sender) {
        MCMetricsAPI api = plugin.getApi();
        if (api == null) {
            sender.sendMessage(colorize(
                    "&cMCMetrics is not properly configured. Please use '/mcmetrics setup' to configure the plugin."));
            return true;
        }

        sender.sendMessage(colorize(PRIMARY_COLOR + "&l⚡ Active A/B Tests"));

        if (plugin.getABTestManager().getActiveTests().isEmpty()) {
            sender.sendMessage(colorize("&7No active A/B tests found."));
            return true;
        }

        for (ABTest test : plugin.getABTestManager().getActiveTests()) {
            sender.sendMessage(colorize("&8&m                                                "));
            sender.sendMessage(colorize(PRIMARY_COLOR + "Name: &f" + test.name));
            sender.sendMessage(colorize(PRIMARY_COLOR + "ID: &7" + test.id));
            sender.sendMessage(colorize(PRIMARY_COLOR + "Trigger: &f" + test.trigger));
            sender.sendMessage(colorize(PRIMARY_COLOR + "Variants:"));

            for (ABTest.ABTestVariant variant : test.variants) {
                String variantInfo = String.format("&8- &f%s &7(%d%%)", variant.name, variant.weight);
                if (variant.action != ABTest.ABTestVariant.ActionType.Control) {
                    variantInfo += colorize(" &8| &7" + variant.action);
                    if (!variant.payload.isEmpty()) {
                        variantInfo += ": &f" + variant.payload;
                    }
                }
                sender.sendMessage(colorize(variantInfo));
            }
        }

        return true;
    }

    private boolean handleABTest(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(colorize("&cUsage: /mcmetrics abtest <test name> <player>"));
            return true;
        }

        String testName = args[1];
        String playerName = args[2];

        // Find the test
        ABTest test = plugin.getABTestManager().getActiveTests().stream()
                .filter(t -> t.name.equalsIgnoreCase(testName))
                .findFirst()
                .orElse(null);

        if (test == null) {
            sender.sendMessage(colorize(
                    "&cA/B test '" + testName + "' not found. Use /mcmetrics abtests to see available tests."));
            return true;
        }

        if (test.trigger != ABTest.TriggerType.Command) {
            sender.sendMessage(colorize("&cA/B test '" + testName + "' is not a command-triggered test."));
            return true;
        }

        // Find the player
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            try {
                UUID uuid = UUID.fromString(playerName);
                player = Bukkit.getPlayer(uuid);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (player == null) {
            sender.sendMessage(colorize("&cPlayer '" + playerName + "' not found or not online."));
            return true;
        }

        ABTest.ABTestVariant variant = plugin.getABTestManager().triggerCommandTest(test, player);
        if (variant != null) {
            sender.sendMessage(colorize(
                    PRIMARY_COLOR + "Successfully triggered A/B test '" + testName + "' on " + player.getName() +
                            " (variant: " + variant.name + ")"));
        }

        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(colorize("&cUsage: /mcmetrics info <player name or uuid>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            try {
                UUID uuid = UUID.fromString(args[1]);
                target = Bukkit.getPlayer(uuid);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (target == null) {
            sender.sendMessage(colorize("&cPlayer not found or not online."));
            return true;
        }

        UUID playerUUID = target.getUniqueId();
        SessionManager sessionManager = plugin.getSessionManager();
        Session session = sessionManager.getSession(playerUUID);

        if (session == null) {
            sender.sendMessage(colorize("&cNo active session found for this player."));
            return true;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sender.sendMessage(colorize(PRIMARY_COLOR + "&lPlayer Info: &f" + target.getName()));
        sender.sendMessage(colorize("&7UUID: &f" + playerUUID));
        sender.sendMessage(colorize("&7Session start: &f" + sdf.format(session.session_start)));
        sender.sendMessage(colorize("&7Domain: &f" + session.domain));
        sender.sendMessage(colorize("&7Player type: &f" + (plugin.isBedrockPlayer(target) ? "Bedrock" : "Java")));
        sender.sendMessage(colorize("&7AFK time: &f" + plugin.getAFKTime(playerUUID) + " seconds"));
        sender.sendMessage(colorize("&7IP Address: &f" + session.ip_address));

        Map<String, Integer> groupedEvents = sessionManager.getGroupedCustomEvents(playerUUID);
        sender.sendMessage(colorize("&7Custom events this session:"));
        for (Map.Entry<String, Integer> entry : groupedEvents.entrySet()) {
            sender.sendMessage(colorize("  &f" + entry.getKey() + ": &7" + entry.getValue() + " times"));
        }

        sender.sendMessage(colorize("&7Payments this session:"));
        for (Payment payment : sessionManager.getSessionPayments(playerUUID)) {
            sender.sendMessage(
                    colorize("  &f" + payment.amount + " " + payment.currency + " &7(" + payment.platform + ")"));
        }

        return true;
    }

    private boolean handleIgnore(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(colorize("&cUsage: /mcmetrics ignore <player name or uuid>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            try {
                UUID uuid = UUID.fromString(args[1]);
                target = Bukkit.getPlayer(uuid);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (target == null) {
            sender.sendMessage(colorize("&cPlayer not found or not online."));
            return true;
        }

        UUID playerUUID = target.getUniqueId();
        SessionManager sessionManager = plugin.getSessionManager();
        Session session = sessionManager.endSession(playerUUID);

        if (session == null) {
            sender.sendMessage(colorize("&cNo active session found for this player."));
            return true;
        }

        sender.sendMessage(colorize(PRIMARY_COLOR + "Player " + target.getName() + " is now being ignored for this session."));

        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(
                colorize(PRIMARY_COLOR + "&lMCMetrics Commands &7(v" + plugin.getDescription().getVersion() + ")"));
        sender.sendMessage(colorize(PRIMARY_COLOR + "/mcmetrics help &7- Show this help message"));
        sender.sendMessage(colorize(PRIMARY_COLOR + "/mcmetrics setup &f<server_id> <server_key>"));
        sender.sendMessage(colorize("  &7- Configure the plugin"));
        sender.sendMessage(colorize(PRIMARY_COLOR + "/mcmetrics reload &7- Reload the configuration"));
        sender.sendMessage(colorize(PRIMARY_COLOR + "/mcmetrics status &7- Show plugin status"));
        sender.sendMessage(colorize(PRIMARY_COLOR + "/mcmetrics abtests &7- List active A/B tests"));
        sender.sendMessage(colorize(PRIMARY_COLOR + "/mcmetrics abtest &f<test name>"));
        sender.sendMessage(colorize("  &7- Trigger a command-based A/B test"));
        sender.sendMessage(colorize(PRIMARY_COLOR + "/mcmetrics info &f<player name or uuid>"));
        sender.sendMessage(colorize("  &7- Show player information"));
        sender.sendMessage(colorize(PRIMARY_COLOR + "/mcmetrics ignore &f<player name or uuid>"));
        sender.sendMessage(colorize("  &7- Ignore a player for the current session"));
        sender.sendMessage(colorize(PRIMARY_COLOR + "/mcmetrics payment &f<platform> <player_uuid>"));
        sender.sendMessage(colorize("  &f<transaction_id> <amount> <currency>"));
        sender.sendMessage(colorize("  &7- Record a payment"));
        sender.sendMessage(colorize(PRIMARY_COLOR + "/mcmetrics customevent &f<player_uuid>"));
        sender.sendMessage(colorize("  &f<event_type> [key1=value1 key2=value2 ...]"));
        sender.sendMessage(colorize(PRIMARY_COLOR + "/mcmetrics customevents &7- List active custom event listeners"));
        sender.sendMessage(colorize("  &7- Record a custom event"));
    }

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&',
                message.replace(PRIMARY_COLOR, ChatColor.of(PRIMARY_COLOR).toString()));
    }
}