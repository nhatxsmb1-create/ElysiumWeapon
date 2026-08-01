package dev.elysium.weapon.listener;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;

public class DatabaseListener implements Listener {

    private org.bukkit.scheduler.BukkitTask autoSaveTask;

    private final ElysiumWeapon plugin;

    public DatabaseListener(ElysiumWeapon plugin) { this.plugin = plugin; }

    public void startAutoSave() {
        autoSaveTask = org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                java.util.UUID uuid = p.getUniqueId();
                PlayerWeaponState state = plugin.getWeaponManager().getState(p);
                java.util.Map<String, Long> dirty = state.flushDirty();
                if (!dirty.isEmpty()) {
                    plugin.getWeaponDatabase().saveAllWeaponExp(uuid, dirty);
                }
            }
        }, 6000L, 6000L); // moi 5 phut (6000 ticks)
    }

    public void stopAutoSave() {
        if (autoSaveTask != null) autoSaveTask.cancel();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        // Load weapon EXP tu DB async, set vao state sau
        java.util.UUID uuid = e.getPlayer().getUniqueId();
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Long> dbData = plugin.getWeaponDatabase().loadWeaponExp(uuid);
            // Sync lai main thread de set vao state
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                PlayerWeaponState state = plugin.getWeaponManager().getState(e.getPlayer());
                state.loadExpFromDb(dbData);
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        java.util.UUID uuid = e.getPlayer().getUniqueId();
        PlayerWeaponState state = plugin.getWeaponManager().getState(e.getPlayer());

        // Save toan bo EXP khi logout
        Map<String, Long> allExp = state.getAllWeaponExp();
        if (!allExp.isEmpty()) {
            plugin.getWeaponDatabase().saveAllWeaponExp(uuid, allExp);
        }

        // Remove state khoi RAM
        plugin.getWeaponManager().removeState(uuid);
    }
}
