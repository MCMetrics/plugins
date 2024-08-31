package net.mcmetrics.plugin;

import net.mcmetrics.shared.config.ConfigManager;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.IOException;
import java.util.logging.Logger;

public final class MCMetricsBungeePlugin extends Plugin {
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager();

        Logger logger = getLogger();

        try {
            configManager.loadConfig("main", getDataFolder(), "config.yml", getResourceAsStream("config.yml"), logger);
            // Load configuration values
            String exampleValue = configManager.getString("main", "example.value");
            getLogger().info("Example value from config: " + exampleValue);
        } catch (IOException e) {
            getLogger().severe("Failed to load configuration: " + e.getMessage());
            return;
        }

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
            getLogger().info("Configurations reloaded successfully.");
        } catch (IOException e) {
            getLogger().severe("Failed to reload configuration: " + e.getMessage());
        }
    }
}