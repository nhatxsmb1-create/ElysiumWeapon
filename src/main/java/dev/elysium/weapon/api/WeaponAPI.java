package dev.elysium.weapon.api;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.mastery.WeaponMastery;
import dev.elysium.weapon.weapon.WeaponData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Public API cho cac plugin khac su dung.
 *
 * Vi du tu ElysiumAdventure:
 *   WeaponAPI.addWeaponExp(player, "HOA_KIEM", 500, "BOSS");
 *
 * Vi du tu ElysiumCombat:
 *   boolean hasWeapon = WeaponAPI.isHoldingElysiumWeapon(player);
 *   double damage = WeaponAPI.getWeaponDamage(player);
 */
public class WeaponAPI {

    private static ElysiumWeapon plugin;

    public static void init(ElysiumWeapon instance) { plugin = instance; }

    // ── Weapon Info ───────────────────────────────────────────────────────────

    public static boolean isHoldingElysiumWeapon(Player player) {
        return plugin.getWeaponManager().getHeldWeaponId(player) != null;
    }

    public static String getHeldWeaponId(Player player) {
        return plugin.getWeaponManager().getHeldWeaponId(player);
    }

    public static WeaponData getHeldWeaponData(Player player) {
        return plugin.getWeaponManager().getHeldWeaponData(player);
    }

    public static double getWeaponBaseDamage(Player player) {
        WeaponData data = plugin.getWeaponManager().getHeldWeaponData(player);
        return data != null ? data.getBaseDamage() : 0;
    }

    // ── Mastery ───────────────────────────────────────────────────────────────

    public static int getWeaponLevel(Player player, String weaponId) {
        return plugin.getWeaponMastery().getWeaponLevel(player, weaponId);
    }

    public static void addWeaponExp(Player player, String weaponId, long amount, String source) {
        WeaponMastery.ExpSource expSource;
        try { expSource = WeaponMastery.ExpSource.valueOf(source.toUpperCase()); }
        catch (Exception e) { expSource = WeaponMastery.ExpSource.DUNGEON; }
        plugin.getWeaponMastery().addExp(player, weaponId, amount, expSource);
    }

    public static boolean hasUnlock(Player player, String weaponId, String unlockId) {
        return plugin.getWeaponMastery().hasUnlock(player, weaponId, unlockId);
    }

    // ── Item ─────────────────────────────────────────────────────────────────

    public static ItemStack createWeaponItem(String weaponId) {
        return plugin.getWeaponManager().createWeaponItem(weaponId);
    }

    public static void giveWeapon(Player player, String weaponId) {
        plugin.getWeaponManager().giveWeapon(player, weaponId);
    }
}
