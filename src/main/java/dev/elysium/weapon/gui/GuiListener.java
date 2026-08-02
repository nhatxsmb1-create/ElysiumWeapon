package dev.elysium.weapon.gui;

import dev.elysium.core.gui.ElysiumGui;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuiListener implements Listener {

    // Dùng ConcurrentHashMap để tránh lỗi Thread Safe khi nhiều người chơi mở GUI cùng lúc
    private static final Map<UUID, ElysiumGui> openGuis = new ConcurrentHashMap<>();

    public static void register(UUID uuid, ElysiumGui gui) { 
        openGuis.put(uuid, gui); 
    }
    
    public static void unregister(UUID uuid) { 
        openGuis.remove(uuid); 
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        ElysiumGui gui = openGuis.get(player.getUniqueId());
        if (gui == null) return;

        Inventory topInv = e.getView().getTopInventory();
        Inventory guiInv = gui.getInventory();

        // 🚨 FIX CHECK EQUALS: So sánh cả Holder lẫn kích thước/type để tránh bẫy Wrapper của Paper/Spigot
        boolean isSameInventory = topInv.equals(guiInv) 
                || (topInv.getHolder() != null && topInv.getHolder().equals(guiInv.getHolder()))
                || (topInv.getType() == guiInv.getType() && topInv.getSize() == guiInv.getSize() && e.getView().getTitle().equals(gui.getTitle()));

        if (!isSameInventory) return;

        // 🛑 TẬN DIỆT LẤY ĐỒ: Hủy hoàn toàn sự kiện Click & đặt Result = DENY
        e.setCancelled(true);
        e.setResult(Event.Result.DENY);

        // Chỉ xử lý logic bấm nút nếu người chơi click vào BẢNG GUI PHÍA TRÊN
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(topInv)) {
            gui.handleClick(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        ElysiumGui gui = openGuis.get(player.getUniqueId());
        if (gui == null) return;

        Inventory topInv = e.getView().getTopInventory();
        Inventory guiInv = gui.getInventory();

        boolean isSameInventory = topInv.equals(guiInv) 
                || (topInv.getHolder() != null && topInv.getHolder().equals(guiInv.getHolder()))
                || (topInv.getType() == guiInv.getType() && topInv.getSize() == guiInv.getSize());

        if (isSameInventory) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;

        ElysiumGui gui = openGuis.remove(player.getUniqueId());
        if (gui != null) {
            gui.onClose(player);
        }
    }
}
