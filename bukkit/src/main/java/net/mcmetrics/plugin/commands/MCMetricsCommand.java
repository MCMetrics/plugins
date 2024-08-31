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
    private final MCMetricsAPI api;

    public MCMetricsCommand(MCMetricsSpigotPlugin plugin, MCMetricsAPI api) {
        this.plugin = plugin;
        this.api = api;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("Usage: /mcmetrics <payment|customevent> ...");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "payment":
                return handlePayment(sender, args);
            case "customevent":
                return handleCustomEvent(sender, args);
            default:
                sender.sendMessage("Unknown subcommand. Use 'payment' or 'customevent'.");
                return true;
        }
    }

    private boolean handlePayment(CommandSender sender, String[] args) {
        if (args.length != 6) {
            sender.sendMessage("Usage: /mcmetrics payment <tebex|craftingstore> <player_uuid> <transaction_id> <amount> <currency>");
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
                    sender.sendMessage("Failed to record payment: " + e.getMessage());
                    return null;
                });

        return true;
    }

    private boolean handleCustomEvent(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /mcmetrics customevent <player_uuid> <event_type> [key1=value1 key2=value2 ...]");
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
                    sender.sendMessage("Failed to record custom event: " + e.getMessage());
                    return null;
                });

        return true;
    }
}