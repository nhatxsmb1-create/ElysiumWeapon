package dev.elysium.weapon.command;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.gui.GuiListener;
import dev.elysium.weapon.gui.WeaponGui;
import dev.elysium.weapon.weapon.WeaponData;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class MasteryCommand implements CommandExecutor {

    private final ElysiumWeapon plugin;

    public MasteryCommand(ElysiumWeapon plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Chi player dung duoc!"); return true;
        }

        // /mas -> mo GUI weapon dang cam, vao thang trang mastery
        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);

        WeaponGui gui = new WeaponGui(plugin);
        if (weapon != null) {
            // Mo thang vao trang mastery cua weapon dang cam
            gui.openMastery(player, weapon.getId());
        } else {
            // Mo trang tong quan
            GuiListener.register(player.getUniqueId(), gui);
            gui.open(player);
            player.sendMessage(color("&7Cam vu khi Elysium de xem mastery chi tiet!"));
        }
        return true;
    }

    private String color(String s) { return s.replace("&", "\u00a7"); }
}
