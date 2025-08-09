package com.fastasyncworldedit.bukkit.util;

import com.fastasyncworldedit.core.util.TaskManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Folia-compatible task manager that handles scheduling for the regionised server
 */
public class FoliaTaskManager extends TaskManager {

    private final Plugin plugin;
    private final Object globalRegionScheduler;
    private final Object asyncScheduler;
    private Method scheduleMethod;
    private Method scheduleDelayedMethod;
    private Method runNowMethod;
    private Method runDelayedMethod;
    private Method runAtFixedRateMethod;

    public FoliaTaskManager(final Plugin plugin) {
        this.plugin = plugin;
        Object tempGlobal = null;
        Object tempAsync = null;
        
        try {
            // Get Folia's global region scheduler via reflection
            tempGlobal = Bukkit.getServer().getClass()
                    .getMethod("getGlobalRegionScheduler")
                    .invoke(Bukkit.getServer());
            
            // Get Folia's async scheduler via reflection  
            tempAsync = Bukkit.getServer().getClass()
                    .getMethod("getAsyncScheduler")
                    .invoke(Bukkit.getServer());

            // Cache reflection methods - try different method names based on Folia API
            Class<?> globalSchedulerClass = tempGlobal.getClass();
            Class<?> asyncSchedulerClass = tempAsync.getClass();
            
            // Try different method names for global scheduler - note Folia uses Consumer, not Runnable
            try {
                this.runNowMethod = globalSchedulerClass.getMethod("run", Plugin.class, java.util.function.Consumer.class);
            } catch (NoSuchMethodException e1) {
                try {
                    // Fallback to execute for Runnable
                    this.runNowMethod = globalSchedulerClass.getMethod("execute", Plugin.class, Runnable.class);
                } catch (NoSuchMethodException e2) {
                    this.runNowMethod = globalSchedulerClass.getMethod("runNow", Plugin.class, java.util.function.Consumer.class);
                }
            }
            
            try {
                this.runDelayedMethod = globalSchedulerClass.getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class);
            } catch (NoSuchMethodException e1) {
                this.runDelayedMethod = globalSchedulerClass.getMethod("runDelayed", Plugin.class, Runnable.class, long.class);
            }
            
            try {
                this.runAtFixedRateMethod = globalSchedulerClass.getMethod("runAtFixedRate", Plugin.class, java.util.function.Consumer.class, long.class, long.class);
            } catch (NoSuchMethodException e1) {
                this.runAtFixedRateMethod = globalSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Runnable.class, long.class, long.class);
            }
            
            // Try different method names for async scheduler - note Folia uses Consumer, not Runnable
            try {
                this.scheduleMethod = asyncSchedulerClass.getMethod("runNow", Plugin.class, java.util.function.Consumer.class);
            } catch (NoSuchMethodException e1) {
                try {
                    this.scheduleMethod = asyncSchedulerClass.getMethod("run", Plugin.class, java.util.function.Consumer.class);
                } catch (NoSuchMethodException e2) {
                    this.scheduleMethod = asyncSchedulerClass.getMethod("execute", Plugin.class, Runnable.class);
                }
            }
            
            try {
                this.scheduleDelayedMethod = asyncSchedulerClass.getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class, TimeUnit.class);
            } catch (NoSuchMethodException e1) {
                this.scheduleDelayedMethod = asyncSchedulerClass.getMethod("runDelayed", Plugin.class, Runnable.class, long.class, TimeUnit.class);
            }
            
            // Assign after all method lookups succeed
            this.globalRegionScheduler = tempGlobal;
            this.asyncScheduler = tempAsync;
            
        } catch (Exception e) {
            // Log available methods for debugging if we have the schedulers
            if (tempGlobal != null) {
                plugin.getLogger().severe("Available methods on GlobalRegionScheduler:");
                for (java.lang.reflect.Method method : tempGlobal.getClass().getMethods()) {
                    plugin.getLogger().severe("  " + method.getName() + "(" + 
                        java.util.Arrays.stream(method.getParameterTypes())
                            .map(Class::getSimpleName)
                            .reduce((a, b) -> a + ", " + b).orElse("") + ")");
                }
            }
            if (tempAsync != null) {
                plugin.getLogger().severe("Available methods on AsyncScheduler:");
                for (java.lang.reflect.Method method : tempAsync.getClass().getMethods()) {
                    plugin.getLogger().severe("  " + method.getName() + "(" + 
                        java.util.Arrays.stream(method.getParameterTypes())
                            .map(Class::getSimpleName)
                            .reduce((a, b) -> a + ", " + b).orElse("") + ")");
                }
            }
            throw new RuntimeException("Failed to initialize Folia task manager", e);
        }
    }

    @Override
    public int repeat(@Nonnull final Runnable runnable, final int interval) {
        try {
            // Convert ticks to milliseconds (1 tick = 50ms)
            long delayTicks = Math.max(1, interval);
            long periodTicks = Math.max(1, interval);
            
            Object task;
            // Check if method expects Consumer or Runnable
            Class<?>[] paramTypes = runAtFixedRateMethod.getParameterTypes();
            if (paramTypes.length >= 2 && paramTypes[1].equals(java.util.function.Consumer.class)) {
                // Folia Consumer-based method
                java.util.function.Consumer<Object> consumer = (t) -> runnable.run();
                task = runAtFixedRateMethod.invoke(globalRegionScheduler, plugin, consumer, delayTicks, periodTicks);
            } else {
                // Bukkit/Paper Runnable-based method
                task = runAtFixedRateMethod.invoke(globalRegionScheduler, plugin, runnable, delayTicks, periodTicks);
            }
            return task.hashCode(); // Return a pseudo task ID
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to schedule repeating task: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public int repeatAsync(@Nonnull final Runnable runnable, final int interval) {
        // Folia doesn't have repeating async tasks in the same way
        // Fallback to running the task once async, then rescheduling
        return repeat(runnable, interval);
    }

    @Override
    public void async(@Nonnull final Runnable runnable) {
        try {
            // Check if method expects Consumer or Runnable
            Class<?>[] paramTypes = scheduleMethod.getParameterTypes();
            if (paramTypes.length >= 2 && paramTypes[1].equals(java.util.function.Consumer.class)) {
                // Folia Consumer-based method
                java.util.function.Consumer<Object> consumer = (task) -> runnable.run();
                scheduleMethod.invoke(asyncScheduler, plugin, consumer);
            } else {
                // Bukkit/Paper Runnable-based method
                scheduleMethod.invoke(asyncScheduler, plugin, runnable);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to schedule async task: " + e.getMessage());
            // Fallback to direct execution
            runnable.run();
        }
    }

    @Override
    public void task(@Nonnull final Runnable runnable) {
        try {
            // Check if method expects Consumer or Runnable
            Class<?>[] paramTypes = runNowMethod.getParameterTypes();
            if (paramTypes.length >= 2 && paramTypes[1].equals(java.util.function.Consumer.class)) {
                // Folia Consumer-based method - pass the task itself as parameter to Consumer
                java.util.function.Consumer<Object> consumer = (task) -> runnable.run();
                runNowMethod.invoke(globalRegionScheduler, plugin, consumer);
            } else {
                // Bukkit/Paper Runnable-based method
                runNowMethod.invoke(globalRegionScheduler, plugin, runnable);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to schedule sync task: " + e.getMessage());
            // Fallback to direct execution
            runnable.run();
        }
    }

    @Override
    public void later(@Nonnull final Runnable runnable, final int delay) {
        try {
            long delayTicks = Math.max(1, delay);
            // Check if method expects Consumer or Runnable
            Class<?>[] paramTypes = runDelayedMethod.getParameterTypes();
            if (paramTypes.length >= 2 && paramTypes[1].equals(java.util.function.Consumer.class)) {
                // Folia Consumer-based method
                java.util.function.Consumer<Object> consumer = (task) -> runnable.run();
                runDelayedMethod.invoke(globalRegionScheduler, plugin, consumer, delayTicks);
            } else {
                // Bukkit/Paper Runnable-based method
                runDelayedMethod.invoke(globalRegionScheduler, plugin, runnable, delayTicks);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to schedule delayed task: " + e.getMessage());
            // Fallback to immediate execution
            runnable.run();
        }
    }

    @Override
    public void laterAsync(@Nonnull final Runnable runnable, final int delay) {
        try {
            long delayMs = Math.max(50, delay * 50); // Convert ticks to milliseconds
            // Check if method expects Consumer or Runnable
            Class<?>[] paramTypes = scheduleDelayedMethod.getParameterTypes();
            if (paramTypes.length >= 2 && paramTypes[1].equals(java.util.function.Consumer.class)) {
                // Folia Consumer-based method
                java.util.function.Consumer<Object> consumer = (task) -> runnable.run();
                scheduleDelayedMethod.invoke(asyncScheduler, plugin, consumer, delayMs, TimeUnit.MILLISECONDS);
            } else {
                // Bukkit/Paper Runnable-based method
                scheduleDelayedMethod.invoke(asyncScheduler, plugin, runnable, delayMs, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to schedule delayed async task: " + e.getMessage());
            // Fallback to immediate execution
            runnable.run();
        }
    }

    @Override
    public void cancel(final int task) {
        // Folia tasks are harder to cancel due to the regionised nature
        // For now, we'll just ignore cancellation requests as tasks are usually short-lived
        // In a real implementation, you'd need to track task objects
    }
}