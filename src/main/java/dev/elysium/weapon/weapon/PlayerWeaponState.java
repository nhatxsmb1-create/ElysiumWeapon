package dev.elysium.weapon.weapon;

import java.util.*;

public class PlayerWeaponState {

    private final UUID playerUuid;
    private String currentWeaponId = null;

    // Cooldown: skillId -> expire millis
    private final Map<String, Long> cooldowns = new HashMap<>();

    // Combo tracking
    private int     comboClicks   = 0;
    private long    lastClickTime = 0L;
    private boolean comboActive   = false;

    // Passive stacks: passiveId -> stack count
    private final Map<String, Integer> passiveStacks  = new HashMap<>();
    private final Map<String, Long>    passiveExpires = new HashMap<>();

    // Weapon EXP: weaponId -> exp (loaded tu DB khi join)
    private final Map<String, Long> weaponExp = new HashMap<>();
    // Track cac weapon da bi thay doi (can save)
    private final Set<String> dirtyWeapons = new HashSet<>();

    private int     normalHitCount    = 0;
    private boolean ultimateCharging  = false;
    private long    chargeStartTime   = 0L;

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

    /** Lay tat ca cooldown hien tai de hien tren actionbar */
    public Map<String, Long> getAllCooldowns() {
        cooldowns.entrySet().removeIf(e -> System.currentTimeMillis() > e.getValue());
        return Collections.unmodifiableMap(cooldowns);
    }

    // ── Combo ─────────────────────────────────────────────────────────────────

    public int registerClick(long windowMs) {
        long now = System.currentTimeMillis();
        if (now - lastClickTime > windowMs) comboClicks = 0;
        comboClicks++;
        lastClickTime = now;
        return comboClicks;
    }

    public void    resetCombo()              { comboClicks = 0; comboActive = false; }
    public boolean isComboActive()           { return comboActive; }
    public void    setComboActive(boolean v) { comboActive = v; }
    public int     getComboClicks()          { return comboClicks; }
    public long    getLastClickTime()        { return lastClickTime; }

    // ── Passive Stack ─────────────────────────────────────────────────────────

    public int addPassiveStack(String passiveId, int max, long durationTicks) {
        long expireMs = System.currentTimeMillis() + durationTicks * 50L;
        Long curExp = passiveExpires.get(passiveId);
        if (curExp != null && System.currentTimeMillis() > curExp) passiveStacks.put(passiveId, 0);
        int next = Math.min(passiveStacks.getOrDefault(passiveId, 0) + 1, max);
        passiveStacks.put(passiveId, next);
        passiveExpires.put(passiveId, expireMs);
        return next;
    }

    public int  getPassiveStack(String id)  { return passiveStacks.getOrDefault(id, 0); }
    public void clearPassiveStack(String id){ passiveStacks.remove(id); passiveExpires.remove(id); }

    // ── Hit Count ─────────────────────────────────────────────────────────────

    public int  incrementHitCount() { return ++normalHitCount; }
    public void resetHitCount()     { normalHitCount = 0; }
    public int  getHitCount()       { return normalHitCount; }

    // ── Weapon EXP (voi DB sync) ──────────────────────────────────────────────

    public void addWeaponExp(String weaponId, long amount) {
        weaponExp.merge(weaponId, amount, Long::sum);
        dirtyWeapons.add(weaponId);
    }

    public long getWeaponExp(String weaponId) {
        return weaponExp.getOrDefault(weaponId, 0L);
    }

    /** Load EXP tu DB vao state khi player join */
    public void loadExpFromDb(Map<String, Long> dbData) {
        weaponExp.putAll(dbData);
    }

    /** Lay danh sach weapon can save va reset dirty set */
    public Map<String, Long> flushDirty() {
        Map<String, Long> toSave = new HashMap<>();
        for (String id : dirtyWeapons) {
            toSave.put(id, weaponExp.getOrDefault(id, 0L));
        }
        dirtyWeapons.clear();
        return toSave;
    }

    /** Lay toan bo exp map de save khi logout */
    public Map<String, Long> getAllWeaponExp() {
        return Collections.unmodifiableMap(weaponExp);
    }

    // ── Ultimate Charge ───────────────────────────────────────────────────────

    public void    startCharge()      { ultimateCharging = true; chargeStartTime = System.currentTimeMillis(); }
    public void    stopCharge()       { ultimateCharging = false; }
    public boolean isCharging()       { return ultimateCharging; }
    public long    chargeElapsed()    { return System.currentTimeMillis() - chargeStartTime; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID   getPlayerUuid()                  { return playerUuid; }
    public String getCurrentWeapon()               { return currentWeaponId; }
    public void   setCurrentWeapon(String id)      { this.currentWeaponId = id; }
}
