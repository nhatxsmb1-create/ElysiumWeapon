package dev.elysium.weapon.listener;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

public class ElysiumDatabaseListener implements Listener {

    private final ElysiumWeapon plugin;

    public ElysiumDatabaseListener(ElysiumWeapon plugin) { 
        this.plugin = plugin; 
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Long> dbData = plugin.getWeaponDatabase().loadWeaponExp(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                PlayerWeaponState state = plugin.getWeaponManager().getState(e.getPlayer());
                state.loadExpFromDb(dbData);
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        PlayerWeaponState state = plugin.getWeaponManager().getState(e.getPlayer());

        Map<String, Long> allExp = state.getAllWeaponExp();
        if (!allExp.isEmpty()) {
            plugin.getWeaponDatabase().saveAllWeaponExp(uuid, allExp);
        }

        plugin.getWeaponManager().removeState(uuid);
    }
}
