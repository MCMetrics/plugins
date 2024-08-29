package net.mcmetrics.plugin;

import net.mcmetrics.sdk.ExampleSDK;
import org.bukkit.plugin.java.JavaPlugin;

public final class MCMetricsSpigotPlugin extends JavaPlugin {
    private ExampleSDK sdk;

    @Override
    public void onEnable() {
       saveDefaultConfig();
       sdk = new ExampleSDK();

       sdk.doSomething();
    }
}
