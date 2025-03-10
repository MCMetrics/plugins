package net.mcmetrics.plugin.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Scheduler adapter implementation for standard Bukkit/Spigot servers
 */
public class BukkitSchedulerAdapter extends SchedulerAdapter {
    
    private final Plugin plugin;
    
    public BukkitSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public Object runTaskTimerAsynchronously(Runnable runnable, long initialDelayTicks, long periodTicks) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                runnable.run();
            }
        }.runTaskTimerAsynchronously(plugin, initialDelayTicks, periodTicks);
        
        return task;
    }
    
    @Override
    public Object runTaskAsynchronously(Runnable runnable) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                runnable.run();
            }
        }.runTaskAsynchronously(plugin);
        
        return task;
    }
    
    @Override
    public Object runTask(Runnable runnable) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                runnable.run();
            }
        }.runTask(plugin);
        
        return task;
    }
    
    @Override
    public void cancelTask(Object taskId) {
        if (taskId instanceof BukkitTask) {
            ((BukkitTask) taskId).cancel();
        }
    }
}
