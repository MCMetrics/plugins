package net.mcmetrics.plugin.scheduler;

import org.bukkit.plugin.Plugin;

/**
 * Abstract scheduler adapter to provide consistent scheduling across different server implementations.
 */
public abstract class SchedulerAdapter {

    /**
     * Creates a new scheduler adapter based on the server type (Folia or Bukkit/Spigot)
     * 
     * @param plugin The plugin instance
     * @return The appropriate scheduler adapter
     */
    public static SchedulerAdapter create(Plugin plugin) {
        // Simple check for Folia - will be false on non-Folia servers
        boolean isFolia = false;
        try {
            // Try to check if server has Folia classes
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            plugin.getLogger().info("Detected Folia server - using Folia scheduler adapter");
        } catch (ClassNotFoundException e) {
            // Not a Folia server
            plugin.getLogger().info("Using standard Bukkit scheduler adapter");
        }
        
        return isFolia ? new FoliaSchedulerAdapter(plugin) : new BukkitSchedulerAdapter(plugin);
    }

    /**
     * Run a task asynchronously on a regular interval
     * 
     * @param runnable The task to run
     * @param initialDelayTicks Initial delay in ticks before first execution
     * @param periodTicks Period in ticks between executions
     * @return A task ID that can be used to cancel the task
     */
    public abstract Object runTaskTimerAsynchronously(Runnable runnable, long initialDelayTicks, long periodTicks);

    /**
     * Run a task asynchronously (one-time execution)
     * 
     * @param runnable The task to run
     * @return A task ID that can be used to cancel the task
     */
    public abstract Object runTaskAsynchronously(Runnable runnable);

    /**
     * Run a task synchronously (on main thread)
     * 
     * @param runnable The task to run
     * @return A task ID that can be used to cancel the task
     */
    public abstract Object runTask(Runnable runnable);

    /**
     * Cancel a scheduled task
     * 
     * @param taskId The task ID to cancel
     */
    public abstract void cancelTask(Object taskId);
}
