package net.mcmetrics.plugin;

import net.mcmetrics.plugin.command.MCMetricsCommand;
import net.mcmetrics.plugin.config.ConfigManager;
import net.mcmetrics.plugin.payment.PaymentManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.logging.Logger;

public final class MCMetricsSpigotPlugin extends JavaPlugin {
    private ExampleSDK sdk;
    private ConfigManager configManager;
    private PaymentManager paymentManager;

    @Override
    public void onEnable() {
        sdk = new ExampleSDK();
        configManager = new ConfigManager();
        paymentManager = new PaymentManager(this);

        Logger logger = getLogger();

        try {
            configManager.loadConfig("main", getDataFolder(), "config.yml", getResource("config.yml"), logger);
            // Load configuration values
            String serverId = configManager.getString("main", "server.id");
            String serverKey = configManager.getString("main", "server.key");
            getLogger().info("Server ID: " + (serverId.isEmpty() ? "Not set" : serverId));
            getLogger().info("Server Key: " + (serverKey.isEmpty() ? "Not set" : "Set (hidden)"));
        } catch (IOException e) {
            getLogger().severe("Failed to load configuration: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register commands
        getCommand("mcmetrics").setExecutor(new MCMetricsCommand(this));

        sdk.doSomething();
    }

    @Override
    public void onDisable() {
        try {
            configManager.saveConfig("main");
        } catch (IOException e) {
            getLogger().severe("Failed to save configuration: " + e.getMessage());
        }
    }

    public void reloadConfig() {
        try {
            configManager.reloadConfig("main");
            getLogger().info("Configuration reloaded successfully.");
        } catch (IOException e) {
            getLogger().severe("Failed to reload configuration: " + e.getMessage());
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PaymentManager getPaymentManager() {
        return paymentManager;
    }
}