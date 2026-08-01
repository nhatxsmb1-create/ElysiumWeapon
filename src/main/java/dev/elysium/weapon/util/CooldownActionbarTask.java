package dev.elysium.weapon.util;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.mastery.WeaponMastery;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import dev.elysium.weapon.weapon.WeaponData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * CooldownActionbarTask
 * Hien thi cooldown tat ca skill + mana tren actionbar moi 0.5 giay.
 *
 * Format:
 * [Skill1: 3s] [Skill2: Ready] [Ult: 12s] | Mana: 80/100
 */
public class CooldownActionbarTask {

    private final ElysiumWeapon plugin;
    private BukkitTask task;

    public CooldownActionbarTask(ElysiumWeapon plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateActionbar(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // moi 0.5 giay
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void updateActionbar(Player player) {
        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        StringBuilder bar = new StringBuilder();

        // Skill 1
        if (weapon.getSkill1() != null) {
            bar.append(buildSkillSlot("P.Phải", weapon.getSkill1().getId(), state));
            bar.append("  ");
        }

        // Skill 2
        if (weapon.getSkill2() != null) {
            bar.append(buildSkillSlot("Sh+Phải", weapon.getSkill2().getId(), state));
            bar.append("  ");
        }

        // Ultimate
        if (weapon.getUltimate() != null) {
            bar.append(buildUltSlot(weapon.getUltimate().getId(), state));
            bar.append("  ");
        }

        // Combo
        if (weapon.getCombo() != null) {
            bar.append(buildComboSlot(weapon.getCombo(), state));
            bar.append("  ");
        }

        // Separator
        bar.append(color("&8|  "));

        // Mana
        try {
            var ep = dev.elysium.core.api.CoreAPI.getPlayer(player);
            int mana    = ep.getMana();
            int maxMana = ep.getMaxMana();
            bar.append(buildManaBar(mana, maxMana));
        } catch (Exception ignored) {}

        // Weapon level
        int wLevel = plugin.getWeaponMastery().getWeaponLevel(player, weapon.getId());
        bar.append(color("  &8| &6Lv." + wLevel));

        player.sendActionBar(bar.toString());
    }

    private String buildSkillSlot(String label, String skillId, PlayerWeaponState state) {
        long cd = state.getCooldownRemaining(skillId);
        if (cd > 0) {
            return color("&7[&f" + label + ": &c" + cd + "s&7]");
        } else {
            return color("&7[&f" + label + ": &aReady&7]");
        }
    }

    private String buildUltSlot(String skillId, PlayerWeaponState state) {
        long cd = state.getCooldownRemaining(skillId);
        if (cd > 0) {
            return color("&7[&cULT: &4" + cd + "s&7]");
        } else {
            return color("&7[&cULT: &a&lREADY&7]");
        }
    }

    private String buildComboSlot(WeaponData.ComboData combo, PlayerWeaponState state) {
        // Hien so clicks hien tai / trigger
        int clicks  = 0; // state khong expose truc tiep, chi hien label
        return color("&7[&eCombo x" + combo.getTriggerClicks() + "&7]");
    }

    private String buildManaBar(int mana, int maxMana) {
        int    bars   = 10;
        int    filled = maxMana > 0 ? (int) ((mana / (double) maxMana) * bars) : 0;
        StringBuilder bar = new StringBuilder(color("&bMP &9["));
        for (int i = 0; i < bars; i++) {
            bar.append(i < filled ? color("&b|") : color("&8|"));
        }
        bar.append(color("&9] &b" + mana + "/" + maxMana));
        return bar.toString();
    }

    private String color(String s) { return s.replace("&", "\u00a7"); }
}
