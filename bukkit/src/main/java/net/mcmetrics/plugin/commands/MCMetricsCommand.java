package net.mcmetrics.plugin.commands;

import net.mcmetrics.plugin.MCMetricsSpigotPlugin;
import net.mcmetrics.shared.MCMetricsAPI;
import net.mcmetrics.shared.models.CustomEvent;
import net.mcmetrics.shared.models.Payment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

public class MCMetricsCommand implements CommandExecutor {

    private final MCMetricsSpigotPlugin plugin;

    public MCMetricsCommand(MCMetricsSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sendHelpMessage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "payment":
                return handlePayment(sender, args);
            case "customevent":
                return handleCustomEvent(sender, args);
            case "reload":
                return handleReload(sender);
            case "setup":
                return handleSetup(sender, args);
            case "help":
                sendHelpMessage(sender);
                return true;
            default:
                sender.sendMessage("Unknown subcommand. Use '/mcmetrics help' for a list of commands.");
                return true;
        }
    }

    private boolean handlePayment(CommandSender sender, String[] args) {
        if (args.length != 6) {
            sender.sendMessage("Usage: /mcmetrics payment <tebex|craftingstore> <player_uuid> <transaction_id> <amount> <currency>");
            return true;
        }

        MCMetricsAPI api = plugin.getApi();
        if (api == null) {
            sender.sendMessage("MCMetrics is not properly configured. Please use '/mcmetrics setup' to configure the plugin.");
            return true;
        }

        Payment payment = new Payment();
        payment.platform = args[1];
        payment.player_uuid = UUID.fromString(args[2]);
        payment.transaction_id = args[3];
        payment.amount = Double.parseDouble(args[4]);
        payment.currency = args[5];
        payment.datetime = new Date();

        api.insertPayment(payment)
                .thenRun(() -> sender.sendMessage("Payment recorded successfully."))
                .exceptionally(e -> {
                    sender.sendMessage("Failed to record payment. Check console for details.");
                    return null;
                });

        return true;
    }

    private boolean handleCustomEvent(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /mcmetrics customevent <player_uuid> <event_type> [key1=value1 key2=value2 ...]");
            return true;
        }

        MCMetricsAPI api = plugin.getApi();
        if (api == null) {
            sender.sendMessage("MCMetrics is not properly configured. Please use '/mcmetrics setup' to configure the plugin.");
            return true;
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
                .thenRun(() -> sender.sendMessage("Custom event recorded successfully."))
                .exceptionally(e -> {
                    sender.sendMessage("Failed to record custom event. Check console for details.");
                    return null;
                });

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("mcmetrics.admin")) {
            sender.sendMessage("You don't have permission to use this command.");
            return true;
        }

        plugin.reloadPlugin();
        sender.sendMessage("MCMetrics configuration reloaded.");
        return true;
    }

    private boolean handleSetup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcmetrics.admin")) {
            sender.sendMessage("You don't have permission to use this command.");
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage("Usage: /mcmetrics setup <server_id> <server_key>");
            return true;
        }

        String serverId = args[1];
        String serverKey = args[2];

        plugin.getConfig().set("server.id", serverId);
        plugin.getConfig().set("server.key", serverKey);
        plugin.saveConfig();
        plugin.reloadPlugin();

        sender.sendMessage("MCMetrics configuration updated and reloaded.");
        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("MCMetrics Commands:");
        sender.sendMessage("/mcmetrics help - Show this help message");
        sender.sendMessage("/mcmetrics setup <server_id> <server_key> - Configure the plugin");
        sender.sendMessage("/mcmetrics reload - Reload the configuration");
        sender.sendMessage("/mcmetrics payment <platform> <player_uuid> <transaction_id> <amount> <currency> - Record a payment");
        sender.sendMessage("/mcmetrics customevent <player_uuid> <event_type> [key1=value1 key2=value2 ...] - Record a custom event");
    }
}