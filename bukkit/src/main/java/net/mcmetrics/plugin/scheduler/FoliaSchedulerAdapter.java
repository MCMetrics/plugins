package net.mcmetrics.plugin.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Scheduler adapter implementation for Folia servers using reflection
 * to avoid direct dependencies on Folia API classes.
 */
public class FoliaSchedulerAdapter extends SchedulerAdapter {
    
    private final Plugin plugin;
    
    public FoliaSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public Object runTaskTimerAsynchronously(Runnable runnable, long initialDelayTicks, long periodTicks) {
        try {
            // Convert ticks to milliseconds (1 tick = 50ms)
            long initialDelayMs = initialDelayTicks * 50;
            long periodMs = periodTicks * 50;
            
            // Get AsyncScheduler via reflection
            Object asyncScheduler = getAsyncScheduler();
            if (asyncScheduler == null) {
                plugin.getLogger().warning("Failed to get AsyncScheduler, falling back to Bukkit scheduler");
                return fallbackToRegularBukkit(runnable, initialDelayTicks, periodTicks);
            }
            
            // Call runAtFixedRate method via reflection
            Method runAtFixedRateMethod = asyncScheduler.getClass().getMethod(
                "runAtFixedRate",
                Plugin.class,
                java.util.function.Consumer.class,
                Duration.class,
                Duration.class,
                TimeUnit.class
            );
            
            return runAtFixedRateMethod.invoke(
                asyncScheduler,
                plugin,
                (java.util.function.Consumer<?>) (task) -> runnable.run(),
                initialDelayMs > 0 ? Duration.ofMillis(initialDelayMs) : Duration.ZERO,
                Duration.ofMillis(periodMs),
                TimeUnit.MILLISECONDS
            );
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error using Folia AsyncScheduler, falling back to Bukkit scheduler", e);
            return fallbackToRegularBukkit(runnable, initialDelayTicks, periodTicks);
        }
    }
    
    @Override
    public Object runTaskAsynchronously(Runnable runnable) {
        try {
            // Get AsyncScheduler via reflection
            Object asyncScheduler = getAsyncScheduler();
            if (asyncScheduler == null) {
                plugin.getLogger().warning("Failed to get AsyncScheduler, falling back to Bukkit scheduler");
                return fallbackToRegularBukkit(runnable);
            }
            
            // Call runNow method via reflection
            Method runNowMethod = asyncScheduler.getClass().getMethod(
                "runNow",
                Plugin.class,
                java.util.function.Consumer.class
            );
            
            return runNowMethod.invoke(
                asyncScheduler,
                plugin,
                (java.util.function.Consumer<?>) (task) -> runnable.run()
            );
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error using Folia AsyncScheduler, falling back to Bukkit scheduler", e);
            return fallbackToRegularBukkit(runnable);
        }
    }
    
    @Override
    public Object runTask(Runnable runnable) {
        try {
            // Get GlobalRegionScheduler via reflection
            Object globalRegionScheduler = getGlobalRegionScheduler();
            if (globalRegionScheduler == null) {
                plugin.getLogger().warning("Failed to get GlobalRegionScheduler, falling back to Bukkit scheduler");
                return fallbackToSyncBukkit(runnable);
            }
            
            // Call run method via reflection
            Method runMethod = globalRegionScheduler.getClass().getMethod(
                "run",
                Plugin.class,
                java.util.function.Consumer.class
            );
            
            return runMethod.invoke(
                globalRegionScheduler,
                plugin,
                (java.util.function.Consumer<?>) (task) -> runnable.run()
            );
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error using Folia GlobalRegionScheduler, falling back to Bukkit scheduler", e);
            return fallbackToSyncBukkit(runnable);
        }
    }
    
    @Override
    public void cancelTask(Object taskId) {
        if (taskId == null) return;
        
        try {
            // Try to cancel via reflection
            Method cancelMethod = taskId.getClass().getMethod("cancel");
            cancelMethod.invoke(taskId);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error cancelling Folia task", e);
        }
    }
    
    private Object getAsyncScheduler() {
        try {
            Method getServerMethod = Bukkit.class.getMethod("getServer");
            Object server = getServerMethod.invoke(null);
            Method getAsyncSchedulerMethod = server.getClass().getMethod("getAsyncScheduler");
            return getAsyncSchedulerMethod.invoke(server);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Cannot find Folia's AsyncScheduler", e);
            return null;
        }
    }
    
    private Object getGlobalRegionScheduler() {
        try {
            Method getServerMethod = Bukkit.class.getMethod("getServer");
            Object server = getServerMethod.invoke(null);
            Method getGlobalRegionSchedulerMethod = server.getClass().getMethod("getGlobalRegionScheduler");
            return getGlobalRegionSchedulerMethod.invoke(server);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Cannot find Folia's GlobalRegionScheduler", e);
            return null;
        }
    }
    
    private Object fallbackToRegularBukkit(Runnable runnable, long initialDelayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, initialDelayTicks, periodTicks);
    }
    
    private Object fallbackToRegularBukkit(Runnable runnable) {
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }
    
    private Object fallbackToSyncBukkit(Runnable runnable) {
        return Bukkit.getScheduler().runTask(plugin, runnable);
    }
}
