package dev.elysium.weapon.command;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.mastery.WeaponMastery;
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

        if (args.length == 0) { sendHelp(player); return true; }

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
                player.sendMessage(color("  &7Type: &f" + weapon.getType() + " | Affinity: &f" + weapon.getAffinity()));
                player.sendMessage(color("  &7Mastery Level: &e" + level + "/50"));
                player.sendMessage(color("  &7EXP: &f" + expCur + "/" + expNext));
                player.sendMessage(color("  &7Base Dame: &f" + weapon.getBaseDamage()));
                if (weapon.getPassive() != null)
                    player.sendMessage(color("  &6[Passive] &f" + weapon.getPassive().getDescription()));
                if (weapon.getSkill1() != null)
                    player.sendMessage(color("  &b[Skill 1] &f" + weapon.getSkill1().getName()
                            + " &7CD: " + (weapon.getSkill1().getCooldown()
                            + plugin.getWeaponMastery().getCooldownModifier(player, weapon.getId(), "SKILL1")) + "s"));
                if (weapon.getSkill2() != null)
                    player.sendMessage(color("  &b[Skill 2] &f" + weapon.getSkill2().getName()
                            + " &7CD: " + (weapon.getSkill2().getCooldown()
                            + plugin.getWeaponMastery().getCooldownModifier(player, weapon.getId(), "SKILL2")) + "s"));
                if (weapon.getUltimate() != null)
                    player.sendMessage(color("  &c[Ultimate] &f" + weapon.getUltimate().getName()
                            + " &7CD: " + (weapon.getUltimate().getCooldown()
                            + plugin.getWeaponMastery().getCooldownModifier(player, weapon.getId(), "ULTIMATE")) + "s"));
                if (weapon.getCombo() != null)
                    player.sendMessage(color("  &e[Combo x" + weapon.getCombo().getTriggerClicks() + "] &f" + weapon.getCombo().getName()));
            }

            case "mastery", "m" -> {
                WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
                if (weapon == null) { player.sendMessage(color("&cBan chua cam vu khi Elysium nao!")); return true; }

                int level = plugin.getWeaponMastery().getWeaponLevel(player, weapon.getId());
                player.sendMessage(color("&5&l=== Mastery: " + weapon.getDisplayName() + " ==="));
                player.sendMessage(color("  &7Level hien tai: &e" + level + "/50"));

                // Hien thi cac unlock
                int[] unlockLevels = {5, 10, 15, 20, 25, 30, 35, 40, 45, 50};
                for (int lv : unlockLevels) {
                    WeaponMastery.MasteryUnlock unlock = plugin.getWeaponMastery().getUnlockAtLevel(lv);
                    if (unlock == null) continue;
                    boolean unlocked = level >= lv;
                    player.sendMessage(color((unlocked ? "&a✔" : "&7○")
                            + " &7Lv." + lv + ": &f" + unlock.description()));
                }
            }

            case "skills", "s" -> {
                WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
                if (weapon == null) { player.sendMessage(color("&cBan chua cam vu khi Elysium nao!")); return true; }

                PlayerWeaponState state = plugin.getWeaponManager().getState(player);
                player.sendMessage(color("&5&l=== Skills: " + weapon.getDisplayName() + " ==="));
                if (weapon.getSkill1() != null) {
                    long cd = state.getCooldownRemaining(weapon.getSkill1().getId());
                    player.sendMessage(color("  &b[P.Phải] &f" + weapon.getSkill1().getName()
                            + (cd > 0 ? " &c(" + cd + "s)" : " &aReady")
                            + " &7- Mana: &b" + weapon.getManaCostSkill1()));
                    player.sendMessage(color("    &7" + weapon.getSkill1().getDescription()));
                }
                if (weapon.getSkill2() != null) {
                    long cd = state.getCooldownRemaining(weapon.getSkill2().getId());
                    player.sendMessage(color("  &b[Shift+P.Phải] &f" + weapon.getSkill2().getName()
                            + (cd > 0 ? " &c(" + cd + "s)" : " &aReady")
                            + " &7- Mana: &b" + weapon.getManaCostSkill2()));
                    player.sendMessage(color("    &7" + weapon.getSkill2().getDescription()));
                }
                if (weapon.getUltimate() != null) {
                    long cd = state.getCooldownRemaining(weapon.getUltimate().getId());
                    player.sendMessage(color("  &c[Shift+P.Trái] &f" + weapon.getUltimate().getName()
                            + (cd > 0 ? " &c(" + cd + "s)" : " &aReady")
                            + " &7- Mana: &b" + weapon.getManaCostUltimate()));
                    player.sendMessage(color("    &7" + weapon.getUltimate().getDescription()));
                }
                if (weapon.getCombo() != null) {
                    player.sendMessage(color("  &e[P.Trái x" + weapon.getCombo().getTriggerClicks() + "] &f" + weapon.getCombo().getName()));
                    player.sendMessage(color("    &7" + weapon.getCombo().getDescription()));
                }
            }

            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage(color("&5=== Weapon ==="));
        p.sendMessage(color("  &7/weapon info &f- Thong tin vu khi dang cam"));
        p.sendMessage(color("  &7/weapon mastery &f- Xem tien trinh Mastery"));
        p.sendMessage(color("  &7/weapon skills &f- Xem tat ca skill + cooldown"));
    }

    private String color(String s) { return s.replace("&", "\u00a7"); }
}
