package net.mcmetrics.plugin.command;

import net.mcmetrics.plugin.MCMetricsSpigotPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MCMetricsCommand implements CommandExecutor, TabCompleter {
    private final MCMetricsSpigotPlugin plugin;

    public MCMetricsCommand(MCMetricsSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(plugin.getConfigManager().getString("main", "admin-permission"))) {
            sender.sendMessage(colorize("##22c55f&lMCMetrics &8» &cYou don't have permission to use this command."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadConfig();
                sender.sendMessage(colorize("##22c55f&lMCMetrics &8» &aConfiguration reloaded successfully."));
                break;
            case "setup":
                if (args.length != 3) {
                    sender.sendMessage(colorize("##22c55f&lMCMetrics &8» &cUsage: /mcmetrics setup <server id> <server key>"));
                    return true;
                }
                String serverId = args[1];
                String serverKey = args[2];
                plugin.getConfigManager().set("main", "server.id", serverId);
                plugin.getConfigManager().set("main", "server.key", serverKey);
                plugin.reloadConfig();
                sender.sendMessage(colorize("##22c55f&lMCMetrics &8» &aServer ID and Key have been set up successfully."));
                break;
            case "payment":
                if (args.length != 7) {
                    sender.sendMessage(colorize("##22c55f&lMCMetrics &8» &cUsage: /mcmetrics payment <tebex|craftingstore> <player_uuid> <transaction_id> <amount> <currency> <package_id>"));
                    return true;
                }
                String platform = args[1];
                String playerUuid = args[2];
                String transactionId = args[3];
                double amount;
                try {
                    amount = Double.parseDouble(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(colorize("##22c55f&lMCMetrics &8» &cInvalid amount. Please enter a valid number."));
                    return true;
                }
                String currency = args[5];
                String packageId = args[6];

                // TODO: save payment
                sender.sendMessage(colorize("##22c55f&lMCMetrics &8» &aPayment saved successfully."));
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(colorize("##22c55f&lMCMetrics &8» &fAvailable commands:"));
        sender.sendMessage(colorize("&8• &f/mcmetrics reload &7- Reload the configuration"));
        sender.sendMessage(colorize("&8• &f/mcmetrics setup <server id> <server key> &7- Set up server ID and key"));
        sender.sendMessage(colorize("&8• &f/mcmetrics payment <tebex|craftingstore> <player_uuid> <transaction_id> <amount> <currency> <package_id> &7- Save a payment"));
    }

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message.replace("##", "#"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("reload", "setup", "payment"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("payment")) {
            completions.addAll(Arrays.asList("tebex", "craftingstore"));
        }

        return completions;
    }
}