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

    private final ElysiumWeapon plugin;

    public DatabaseListener(ElysiumWeapon plugin) { this.plugin = plugin; }

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
