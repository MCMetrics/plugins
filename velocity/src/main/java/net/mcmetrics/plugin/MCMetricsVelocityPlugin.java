package net.mcmetrics.plugin;

import net.mcmetrics.sdk.ExampleSDK;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

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

    @Inject
    public MCMetricsVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.exampleSDK = new ExampleSDK();
    }

    @Subscribe
    public void onEnable(ProxyInitializeEvent event) {
        exampleSDK.doSomething();
    }
}
