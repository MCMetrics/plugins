package net.mcmetrics.plugin;

import net.mcmetrics.plugin.config.ConfigManager;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.logging.Logger;

@Plugin(
        id = "mcmetrics",
        name = "MCMetrics",
        version = "@VERSION@",
        description = "The MCMetrics Velocity plugin"
)
public final class MCMetricsVelocityPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final ExampleSDK exampleSDK;
    private ConfigManager configManager;

    @Inject
    public MCMetricsVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.exampleSDK = new ExampleSDK();
        this.configManager = new ConfigManager();
    }

    @Subscribe
    public void onEnable(ProxyInitializeEvent event) {
        try {
            InputStream defaultMainConfig = getClass().getResourceAsStream("/config.yml");
            if (defaultMainConfig == null) {
                logger.severe("Default configuration files not found!");
                return;
            }

            configManager.loadConfig("main", dataDirectory.toFile(), "config.yml", defaultMainConfig, logger);
            // Load configuration values
            String exampleValue = configManager.getString("main", "example.value");
            logger.info("Example value from config: " + exampleValue);
        } catch (IOException e) {
            logger.severe("Failed to load configuration: " + e.getMessage());
            return;
        }

        exampleSDK.doSomething();
    }

    @Subscribe
    public void onDisable(ProxyShutdownEvent event) {
        try {
            configManager.saveConfig("main");
        } catch (IOException e) {
            logger.severe("Failed to save configuration: " + e.getMessage());
        }
    }

    public void reloadConfig() {
        try {
            configManager.reloadConfig("main");
            logger.info("Configurations reloaded successfully.");
        } catch (IOException e) {
            logger.severe("Failed to reload configuration: " + e.getMessage());
        }
    }
}