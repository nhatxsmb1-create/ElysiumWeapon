package dev.elysium.weapon.mastery;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Weapon Mastery System
 * Choi dungeon/boss/pvp/quest -> nhan Weapon EXP -> len cap -> mo Skill/Skin/Effect
 *
 * Level 1-10: Skill 1 mo khoa dan
 * Level 11-20: Skill 2 mo khoa dan
 * Level 21-30: Ultimate mo khoa dan + Passive upgrade
 * Level 31-50: Skin + Effect + Passive tier 2
 */
public class WeaponMastery {

    // EXP can de len cap: cap -> exp
    private static final Map<Integer, Long> EXP_TABLE = new LinkedHashMap<>();

    static {
        long base = 100;
        for (int i = 1; i <= 50; i++) {
            EXP_TABLE.put(i, (long) (base * Math.pow(1.3, i - 1)));
        }
    }

    private final ElysiumWeapon plugin;

    public WeaponMastery(ElysiumWeapon plugin) {
        this.plugin = plugin;
    }

    // ── Add EXP ───────────────────────────────────────────────────────────────

    /**
     * Them EXP cho weapon, xu ly level up.
     * Source: DUNGEON, BOSS, PVP, QUEST, EVENT
     */
    public void addExp(Player player, String weaponId, long amount, ExpSource source) {
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        // Multiplier theo nguon
        double mult = switch (source) {
            case DUNGEON -> 1.0;
            case BOSS    -> 1.5;
            case PVP     -> 1.2;
            case QUEST   -> 1.0;
            case EVENT   -> 2.0;
        };

        long finalExp = (long) (amount * mult);
        state.addWeaponExp(weaponId, finalExp);

        long totalExp = state.getWeaponExp(weaponId);
        int  curLevel = getLevelFromExp(totalExp);
        int  oldLevel = getLevelFromExp(totalExp - finalExp);

        if (curLevel > oldLevel) {
            handleLevelUp(player, weaponId, oldLevel, curLevel);
        }

        // Cap nhat lore tren item sau moi lan them EXP
        refreshWeaponLore(player, weaponId);

        // Actionbar thong bao EXP
        long expForNext = getExpForNextLevel(curLevel);
        long expInLevel = totalExp - getExpForLevel(curLevel);
        player.sendActionBar(color("&6[" + getWeaponDisplayName(weaponId) + " Lv." + curLevel + "] "
                + "&e+" + finalExp + " EXP &7| &f" + expInLevel + "/" + expForNext));
    }

    /** Cap nhat lore tren item weapon trong tay player */
    public void refreshWeaponLore(Player player, String weaponId) {
        org.bukkit.inventory.ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || !held.hasItemMeta()) return;

        // Kiem tra xem item co phai weapon nay khong
        var pdc = held.getItemMeta().getPersistentDataContainer();
        var key = new org.bukkit.NamespacedKey(plugin, dev.elysium.weapon.weapon.WeaponManager.WEAPON_ID_KEY);
        if (!pdc.has(key)) return;
        String heldId = pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);
        if (!weaponId.equals(heldId)) return;

        // Tao item moi voi lore da cap nhat
        org.bukkit.inventory.ItemStack updated = plugin.getWeaponManager().createWeaponItemForPlayer(weaponId, player);
        if (updated == null) return;

        // Giu nguyen slot hien tai
        int slot = player.getInventory().getHeldItemSlot();
        player.getInventory().setItem(slot, updated);
    }

    private void handleLevelUp(Player player, String weaponId, int oldLevel, int newLevel) {
        for (int lv = oldLevel + 1; lv <= newLevel; lv++) {
            MasteryUnlock unlock = getUnlockAtLevel(lv);
            String msg = color("&6&l[Weapon Mastery] &f"
                    + getWeaponDisplayName(weaponId)
                    + " &elen " + lv + "!");

            if (unlock != null) {
                msg += color("\n&a→ Mo khoa: &f" + unlock.description());
                player.sendMessage(color("&a✦ Mo khoa: &f" + unlock.description()));
            }

            player.sendTitle(
                    color("&6&lWeapon Lv." + lv),
                    color("&f" + getWeaponDisplayName(weaponId)),
                    10, 50, 10
            );
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
    }

    // ── Unlock System ─────────────────────────────────────────────────────────

    public MasteryUnlock getUnlockAtLevel(int level) {
        return switch (level) {
            case 5  -> new MasteryUnlock("SKILL1_UPGRADE", "Skill 1 - Giam cooldown 1s");
            case 10 -> new MasteryUnlock("SKILL1_MAX",     "Skill 1 - Damage +20%");
            case 15 -> new MasteryUnlock("SKILL2_UPGRADE", "Skill 2 - Giam cooldown 2s");
            case 20 -> new MasteryUnlock("SKILL2_MAX",     "Skill 2 - Them hieu ung phu");
            case 25 -> new MasteryUnlock("ULTIMATE_UPGRADE","Ultimate - Giam cooldown 5s");
            case 30 -> new MasteryUnlock("ULTIMATE_MAX",   "Ultimate - Damage +30% + Hieu ung moi");
            case 35 -> new MasteryUnlock("PASSIVE_TIER2",  "Passive - Nang cap tier 2");
            case 40 -> new MasteryUnlock("SKIN_UNLOCK",    "Mo khoa Skin Basic");
            case 45 -> new MasteryUnlock("EFFECT_UNLOCK",  "Mo khoa Kill Effect");
            case 50 -> new MasteryUnlock("MASTERY_COMPLETE","MASTERY HOAN THANH - Mo khoa Skin Mastery");
            default -> null;
        };
    }

    public boolean hasUnlock(Player player, String weaponId, String unlockId) {
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        long exp   = state.getWeaponExp(weaponId);
        int  level = getLevelFromExp(exp);

        return switch (unlockId) {
            case "SKILL1_UPGRADE"    -> level >= 5;
            case "SKILL1_MAX"        -> level >= 10;
            case "SKILL2_UPGRADE"    -> level >= 15;
            case "SKILL2_MAX"        -> level >= 20;
            case "ULTIMATE_UPGRADE"  -> level >= 25;
            case "ULTIMATE_MAX"      -> level >= 30;
            case "PASSIVE_TIER2"     -> level >= 35;
            case "SKIN_UNLOCK"       -> level >= 40;
            case "EFFECT_UNLOCK"     -> level >= 45;
            case "MASTERY_COMPLETE"  -> level >= 50;
            default -> false;
        };
    }

    /** Lay skill cooldown modifier theo unlock */
    public int getCooldownModifier(Player player, String weaponId, String skillSlot) {
        int mod = 0;
        if (skillSlot.equals("SKILL1") && hasUnlock(player, weaponId, "SKILL1_UPGRADE")) mod -= 1;
        if (skillSlot.equals("SKILL2") && hasUnlock(player, weaponId, "SKILL2_UPGRADE")) mod -= 2;
        if (skillSlot.equals("ULTIMATE") && hasUnlock(player, weaponId, "ULTIMATE_UPGRADE")) mod -= 5;
        return mod;
    }

    /** Lay damage multiplier bonus theo unlock */
    public double getDamageBonus(Player player, String weaponId, String skillSlot) {
        double bonus = 1.0;
        if (skillSlot.equals("SKILL1") && hasUnlock(player, weaponId, "SKILL1_MAX"))    bonus += 0.2;
        if (skillSlot.equals("ULTIMATE") && hasUnlock(player, weaponId, "ULTIMATE_MAX")) bonus += 0.3;
        return bonus;
    }

    // ── Level Calc ────────────────────────────────────────────────────────────

    public int getLevelFromExp(long totalExp) {
        int level = 1;
        long cumulative = 0;
        for (Map.Entry<Integer, Long> entry : EXP_TABLE.entrySet()) {
            cumulative += entry.getValue();
            if (totalExp >= cumulative) level = entry.getKey() + 1;
            else break;
        }
        return Math.min(level, 50);
    }

    public long getExpForLevel(int level) {
        long cumulative = 0;
        for (Map.Entry<Integer, Long> entry : EXP_TABLE.entrySet()) {
            if (entry.getKey() >= level) break;
            cumulative += entry.getValue();
        }
        return cumulative;
    }

    public long getExpForNextLevel(int currentLevel) {
        return EXP_TABLE.getOrDefault(currentLevel, 999999L);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int getWeaponLevel(Player player, String weaponId) {
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        return getLevelFromExp(state.getWeaponExp(weaponId));
    }

    private String getWeaponDisplayName(String weaponId) {
        var data = plugin.getWeaponManager().getWeaponData(weaponId);
        return data != null ? data.getDisplayName().replaceAll("§.", "") : weaponId;
    }

    private String color(String s) { return s.replace("&", "\u00a7"); }

    // ── Enums & Records ───────────────────────────────────────────────────────

    public enum ExpSource { DUNGEON, BOSS, PVP, QUEST, EVENT }

    public record MasteryUnlock(String id, String description) {}
}
