package dev.elysium.weapon.command;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.mastery.WeaponMastery;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class WeaponAdminCommand implements CommandExecutor {

    private final ElysiumWeapon plugin;

    public WeaponAdminCommand(ElysiumWeapon plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("elysium.weapon.admin")) {
            sender.sendMessage(color("&cKhong co quyen!")); return true;
        }

        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {

            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage(color("&cDung: /wpadmin give <player> <weapon_id>")); return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(color("&cPlayer khong online!")); return true; }
                plugin.getWeaponManager().giveWeapon(target, args[2].toUpperCase());
                sender.sendMessage(color("&aDa cho &f" + target.getName() + " &avu khi &f" + args[2]));
            }

            case "list" -> {
                sender.sendMessage(color("&5=== Danh sach Weapon ==="));
                for (String id : plugin.getWeaponManager().getWeaponIds()) {
                    var data = plugin.getWeaponManager().getWeaponData(id);
                    sender.sendMessage(color("  &e" + id + " &7- " + data.getDisplayName()
                            + " &f| " + data.getType() + " | " + data.getAffinity()));
                }
            }

            case "addexp" -> {
                if (args.length < 4) {
                    sender.sendMessage(color("&cDung: /wpadmin addexp <player> <weapon_id> <amount>")); return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(color("&cPlayer khong online!")); return true; }
                try {
                    long amount = Long.parseLong(args[3]);
                    plugin.getWeaponMastery().addExp(target, args[2].toUpperCase(),
                            amount, WeaponMastery.ExpSource.EVENT);
                    sender.sendMessage(color("&aDa them &e" + amount + " &aWeapon EXP cho &f" + target.getName()));
                } catch (NumberFormatException e) {
                    sender.sendMessage(color("&cAmount phai la so!"));
                }
            }

            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(color("&aReloaded config!"));
            }

            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage(color("&5=== Weapon Admin ==="));
        s.sendMessage(color("  &7/wpadmin give <player> <id> &f- Cho vu khi"));
        s.sendMessage(color("  &7/wpadmin list &f- Danh sach vu khi"));
        s.sendMessage(color("  &7/wpadmin addexp <player> <id> <amount> &f- Them EXP"));
        s.sendMessage(color("  &7/wpadmin reload &f- Reload config"));
    }

    private String color(String s) { return s.replace("&", "\u00a7"); }
}
