package dev.elysium.weapon.weapon;

import java.util.*;

/**
 * Trang thai vu khi runtime cua moi player.
 * Luu: cooldown, combo click, passive stack, weapon EXP.
 */
public class PlayerWeaponState {

    private final UUID playerUuid;

    // Weapon dang cam
    private String currentWeaponId = null;

    // Cooldown: skillId -> expire millis
    private final Map<String, Long> cooldowns = new HashMap<>();

    // Combo tracking
    private int  comboClicks    = 0;
    private long lastClickTime  = 0L;
    private boolean comboActive = false;

    // Passive stacks: passiveId -> stack count
    private final Map<String, Integer> passiveStacks   = new HashMap<>();
    private final Map<String, Long>    passiveExpires  = new HashMap<>();

    // Weapon EXP: weaponId -> exp
    private final Map<String, Long> weaponExp = new HashMap<>();

    // Passive tracker misc
    private int normalHitCount = 0;   // Dung cho HEAVY_BLOW, charge count

    // Ultimate charge (cho CHARGE_SHOT type)
    private boolean ultimateCharging = false;
    private long    chargeStartTime  = 0L;

    public PlayerWeaponState(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    // ── Cooldown ─────────────────────────────────────────────────────────────

    public void setCooldown(String skillId, int seconds) {
        cooldowns.put(skillId, System.currentTimeMillis() + seconds * 1000L);
    }

    public boolean isOnCooldown(String skillId) {
        Long exp = cooldowns.get(skillId);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) { cooldowns.remove(skillId); return false; }
        return true;
    }

    public long getCooldownRemaining(String skillId) {
        Long exp = cooldowns.get(skillId);
        if (exp == null) return 0;
        return Math.max(0, (exp - System.currentTimeMillis()) / 1000);
    }

    // ── Combo ─────────────────────────────────────────────────────────────────

    /** Ghi nhan 1 click, tra ve so clicks hien tai trong window */
    public int registerClick(long windowMs) {
        long now = System.currentTimeMillis();
        if (now - lastClickTime > windowMs) {
            comboClicks = 0;
        }
        comboClicks++;
        lastClickTime = now;
        return comboClicks;
    }

    public void resetCombo() { comboClicks = 0; comboActive = false; }
    public boolean isComboActive()          { return comboActive; }
    public void    setComboActive(boolean v){ comboActive = v; }

    // ── Passive Stack ─────────────────────────────────────────────────────────

    public int addPassiveStack(String passiveId, int max, long durationTicks) {
        long expireMs = System.currentTimeMillis() + durationTicks * 50L;
        // Reset neu het han
        Long curExp = passiveExpires.get(passiveId);
        if (curExp != null && System.currentTimeMillis() > curExp) {
            passiveStacks.put(passiveId, 0);
        }
        int cur = passiveStacks.getOrDefault(passiveId, 0);
        int next = Math.min(cur + 1, max);
        passiveStacks.put(passiveId, next);
        passiveExpires.put(passiveId, expireMs);
        return next;
    }

    public int  getPassiveStack(String passiveId)  { return passiveStacks.getOrDefault(passiveId, 0); }
    public void clearPassiveStack(String passiveId) { passiveStacks.remove(passiveId); passiveExpires.remove(passiveId); }

    // ── Normal Hit Count (cho Heavy Blow v.v.) ────────────────────────────────

    public int  incrementHitCount()   { return ++normalHitCount; }
    public void resetHitCount()       { normalHitCount = 0; }
    public int  getHitCount()         { return normalHitCount; }

    // ── Weapon EXP ───────────────────────────────────────────────────────────

    public void addWeaponExp(String weaponId, long amount) {
        weaponExp.merge(weaponId, amount, Long::sum);
    }

    public long getWeaponExp(String weaponId) {
        return weaponExp.getOrDefault(weaponId, 0L);
    }

    // ── Ultimate Charge ───────────────────────────────────────────────────────

    public void startCharge()       { ultimateCharging = true; chargeStartTime = System.currentTimeMillis(); }
    public void stopCharge()        { ultimateCharging = false; }
    public boolean isCharging()     { return ultimateCharging; }
    public long    chargeElapsed()  { return System.currentTimeMillis() - chargeStartTime; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID   getPlayerUuid()    { return playerUuid; }
    public String getCurrentWeapon() { return currentWeaponId; }
    public void   setCurrentWeapon(String id) { this.currentWeaponId = id; }
}
