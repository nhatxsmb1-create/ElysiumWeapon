package dev.elysium.weapon.gui;

import dev.elysium.core.gui.ElysiumGui;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuiListener implements Listener {

    private static final Map<UUID, ElysiumGui> openGuis = new HashMap<>();

    public static void register(UUID uuid, ElysiumGui gui) { openGuis.put(uuid, gui); }
    public static void unregister(UUID uuid)               { openGuis.remove(uuid); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ElysiumGui gui = openGuis.get(player.getUniqueId());
        if (gui == null) return;

        // Cancel TAT CA click khi GUI dang mo
        // ke ca click vao tui do phia duoi
        e.setCancelled(true);

        // Chi xu ly click neu dung vao GUI inventory (khong phai tui do)
        if (e.getInventory().equals(gui.getInventory())) {
            gui.handleClick(e);
        }
    }

    // Cancel drag event de khong drag item ra ngoai
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ElysiumGui gui = openGuis.get(player.getUniqueId());
        if (gui == null) return;
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
