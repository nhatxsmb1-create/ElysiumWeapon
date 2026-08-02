package dev.elysium.weapon.command;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.gui.WeaponGui;
import dev.elysium.weapon.weapon.WeaponData;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class WeaponCommand implements CommandExecutor {

    private final ElysiumWeapon plugin;

    public WeaponCommand(ElysiumWeapon plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Chi player dung duoc!"); return true;
        }

        // /weapon hoac /wp -> mo GUI
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            openGui(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info", "i" -> {
                WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
                if (weapon == null) { player.sendMessage(color("&cBan chua cam vu khi Elysium nao!")); return true; }
                int   level   = plugin.getWeaponMastery().getWeaponLevel(player, weapon.getId());
                PlayerWeaponState state = plugin.getWeaponManager().getState(player);
                long  totalExp = state.getWeaponExp(weapon.getId());
                long  expNext  = plugin.getWeaponMastery().getExpForNextLevel(level);
                long  expCur   = totalExp - plugin.getWeaponMastery().getExpForLevel(level);

                player.sendMessage(color("&5&l=== " + weapon.getDisplayName() + " ==="));
                player.sendMessage(color("  &7Mastery Level: &e" + level + "/50"));
                player.sendMessage(color("  &7EXP: &f" + expCur + "/" + expNext));
                player.sendMessage(color("  &7Dame: &f" + weapon.getBaseDamage()));
                if (weapon.getPassive() != null)
                    player.sendMessage(color("  &6[Passive] &f" + weapon.getPassive().getDescription()));
            }
            case "mastery", "m" -> openGui(player);
            case "skills", "s"  -> {
                WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
                if (weapon == null) { player.sendMessage(color("&cBan chua cam vu khi Elysium nao!")); return true; }
                PlayerWeaponState state = plugin.getWeaponManager().getState(player);
                player.sendMessage(color("&5=== Skills: " + weapon.getDisplayName() + " ==="));
                if (weapon.getSkill1() != null) {
                    long cd = state.getCooldownRemaining(weapon.getSkill1().getId());
                    player.sendMessage(color("  &b[P.Phải] &f" + weapon.getSkill1().getName()
                            + (cd > 0 ? " &c(" + cd + "s)" : " &aReady") + " &7Mana: &b" + weapon.getManaCostSkill1()));
                }
                if (weapon.getSkill2() != null) {
                    long cd = state.getCooldownRemaining(weapon.getSkill2().getId());
                    player.sendMessage(color("  &b[Shift+P.P] &f" + weapon.getSkill2().getName()
                            + (cd > 0 ? " &c(" + cd + "s)" : " &aReady") + " &7Mana: &b" + weapon.getManaCostSkill2()));
                }
                if (weapon.getUltimate() != null) {
                    long cd = state.getCooldownRemaining(weapon.getUltimate().getId());
                    player.sendMessage(color("  &c[Shift+P.T] &f" + weapon.getUltimate().getName()
                            + (cd > 0 ? " &c(" + cd + "s)" : " &a&lREADY") + " &7Mana: &b" + weapon.getManaCostUltimate()));
                }
                if (weapon.getCombo() != null)
                    player.sendMessage(color("  &e[P.Trái x" + weapon.getCombo().getTriggerClicks() + "] &f" + weapon.getCombo().getName()));
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void openGui(Player player) {
        new WeaponGui(plugin).open(player);
    }

    private void sendHelp(Player p) {
        p.sendMessage(color("&5=== Weapon ==="));
        p.sendMessage(color("  &7/weapon &f- Mo menu GUI"));
        p.sendMessage(color("  &7/weapon info &f- Thong tin vu khi"));
        p.sendMessage(color("  &7/weapon skills &f- Xem skill + cooldown"));
    }

    private String color(String s) { return s.replace("&", "\u00a7"); }
}
