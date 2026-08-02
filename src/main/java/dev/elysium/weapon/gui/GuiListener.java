package dev.elysium.weapon.gui;

import dev.elysium.core.gui.ElysiumGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuiListener implements Listener {

    private static final Map<UUID, ElysiumGui> openGuis = new ConcurrentHashMap<>();

    public static void register(UUID uuid, ElysiumGui gui) { 
        openGuis.put(uuid, gui); 
    }
    
    public static void unregister(UUID uuid) { 
        openGuis.remove(uuid); 
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ElysiumGui gui = openGuis.get(player.getUniqueId());
        if (gui == null) return;

        // Kiem tra top inventory cua view co phai GUI khong
        if (!e.getView().getTopInventory().equals(gui.getInventory())) return;

        // Cancel TAT CA click - ca click vao GUI lan tui do ben duoi
        e.setCancelled(true);

        // Chi goi handleClick neu click vao phan GUI (khong phai tui do)
        if (e.getClickedInventory() != null
                && e.getClickedInventory().equals(gui.getInventory())) {
            gui.handleClick(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ElysiumGui gui = openGuis.get(player.getUniqueId());
        if (gui == null) return;
        if (!e.getView().getTopInventory().equals(gui.getInventory())) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        ElysiumGui gui = openGuis.get(player.getUniqueId());
        if (gui == null) return;
        gui.onClose(player);
        openGuis.remove(player.getUniqueId());
    }
}
