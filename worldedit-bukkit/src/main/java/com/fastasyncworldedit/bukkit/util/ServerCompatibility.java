package com.fastasyncworldedit.bukkit.util;

import org.bukkit.Bukkit;

/**
 * Utility class to detect server implementations and their capabilities
 */
public class ServerCompatibility {

    private static Boolean isFolia;
    private static Boolean isPaper;

    /**
     * Check if the server is running Folia
     * @return true if running on Folia
     */
    public static boolean isFolia() {
        if (isFolia == null) {
            try {
                // Folia has the getGlobalRegionScheduler method
                Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler");
                isFolia = true;
            } catch (NoSuchMethodException e) {
                isFolia = false;
            }
        }
        return isFolia;
    }

    /**
     * Check if the server is running Paper (but not Folia)
     * @return true if running on Paper
     */
    public static boolean isPaper() {
        if (isPaper == null) {
            try {
                Class.forName("io.papermc.paper.event.player.PlayerItemFrameChangeEvent");
                isPaper = !isFolia(); // Paper but not Folia
            } catch (ClassNotFoundException e) {
                isPaper = false;
            }
        }
        return isPaper;
    }

    /**
     * Get server implementation name for logging
     * @return server implementation name
     */
    public static String getServerImplementation() {
        if (isFolia()) {
            return "Folia";
        } else if (isPaper()) {
            return "Paper";
        } else {
            return "Spigot/Bukkit";
        }
    }
}