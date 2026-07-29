package dev.elysium.weapon.weapon;

import java.util.List;

public class WeaponData {

    public enum WeaponType { SWORD, GREATSWORD, STAFF, BOW, KATANA, DAGGER, SCYTHE, SPEAR, ORB }
    public enum ClassAffinity { WARRIOR, MAGE, RANGER, ASSASSIN, SUPPORT, NONE }

    private final String       id;
    private final String       displayName;
    private final WeaponType   type;
    private final ClassAffinity affinity;
    private final String       material;
    private final int          modelData;
    private final List<String> lore;
    private final double       baseDamage;
    private final double       baseSpeed;
    private final int          manaCostSkill1;
    private final int          manaCostSkill2;
    private final int          manaCostUltimate;

    private final PassiveData  passive;
    private final SkillData    skill1;
    private final SkillData    skill2;
    private final SkillData    ultimate;
    private final ComboData    combo;

    public WeaponData(String id, String displayName, WeaponType type, ClassAffinity affinity,
                      String material, int modelData, List<String> lore,
                      double baseDamage, double baseSpeed,
                      int manaCostSkill1, int manaCostSkill2, int manaCostUltimate,
                      PassiveData passive, SkillData skill1, SkillData skill2,
                      SkillData ultimate, ComboData combo) {
        this.id              = id;
        this.displayName     = displayName;
        this.type            = type;
        this.affinity        = affinity;
        this.material        = material;
        this.modelData       = modelData;
        this.lore            = lore;
        this.baseDamage      = baseDamage;
        this.baseSpeed       = baseSpeed;
        this.manaCostSkill1  = manaCostSkill1;
        this.manaCostSkill2  = manaCostSkill2;
        this.manaCostUltimate= manaCostUltimate;
        this.passive         = passive;
        this.skill1          = skill1;
        this.skill2          = skill2;
        this.ultimate        = ultimate;
        this.combo           = combo;
    }

    public String        getId()              { return id; }
    public String        getDisplayName()     { return displayName; }
    public WeaponType    getType()            { return type; }
    public ClassAffinity getAffinity()        { return affinity; }
    public String        getMaterial()        { return material; }
    public int           getModelData()       { return modelData; }
    public List<String>  getLore()            { return lore; }
    public double        getBaseDamage()      { return baseDamage; }
    public double        getBaseSpeed()       { return baseSpeed; }
    public int           getManaCostSkill1()  { return manaCostSkill1; }
    public int           getManaCostSkill2()  { return manaCostSkill2; }
    public int           getManaCostUltimate(){ return manaCostUltimate; }
    public PassiveData   getPassive()         { return passive; }
    public SkillData     getSkill1()          { return skill1; }
    public SkillData     getSkill2()          { return skill2; }
    public SkillData     getUltimate()        { return ultimate; }
    public ComboData     getCombo()           { return combo; }

    // ── Inner: PassiveData ────────────────────────────────────────────────────

    public static class PassiveData {
        private final String id;
        private final String description;
        private final java.util.Map<String, Object> properties;

        public PassiveData(String id, String description, java.util.Map<String, Object> properties) {
            this.id          = id;
            this.description = description;
            this.properties  = properties;
        }

        public String getId()          { return id; }
        public String getDescription() { return description; }
        public Object get(String key)  { return properties.get(key); }
        public double getDouble(String key, double def) {
            Object v = properties.get(key);
            return v instanceof Number n ? n.doubleValue() : def;
        }
        public int getInt(String key, int def) {
            Object v = properties.get(key);
            return v instanceof Number n ? n.intValue() : def;
        }
    }

    // ── Inner: SkillData ──────────────────────────────────────────────────────

    public static class SkillData {
        private final String id;
        private final String name;
        private final String description;
        private final int    cooldown;           // seconds
        private final String animationType;
        private final String particleType;
        private final String soundType;
        private final java.util.Map<String, Object> properties;

        public SkillData(String id, String name, String description, int cooldown,
                         String animationType, String particleType, String soundType,
                         java.util.Map<String, Object> properties) {
            this.id            = id;
            this.name          = name;
            this.description   = description;
            this.cooldown      = cooldown;
            this.animationType = animationType;
            this.particleType  = particleType;
            this.soundType     = soundType;
            this.properties    = properties;
        }

        public String getId()            { return id; }
        public String getName()          { return name; }
        public String getDescription()   { return description; }
        public int    getCooldown()      { return cooldown; }
        public String getAnimationType() { return animationType; }
        public String getParticleType()  { return particleType; }
        public String getSoundType()     { return soundType; }
        public double getDouble(String key, double def) {
            Object v = properties.get(key);
            return v instanceof Number n ? n.doubleValue() : def;
        }
        public int getInt(String key, int def) {
            Object v = properties.get(key);
            return v instanceof Number n ? n.intValue() : def;
        }
        public boolean getBoolean(String key, boolean def) {
            Object v = properties.get(key);
            return v instanceof Boolean b ? b : def;
        }
    }

    // ── Inner: ComboData ──────────────────────────────────────────────────────

    public static class ComboData {
        private final String       id;
        private final String       name;
        private final String       description;
        private final int          triggerClicks;
        private final long         windowMs;
        private final List<Double> damages;
        private final String       animationType;
        private final String       particleType;
        private final String       soundType;
        private final java.util.Map<String, Object> properties;

        public ComboData(String id, String name, String description,
                         int triggerClicks, long windowMs, List<Double> damages,
                         String animationType, String particleType, String soundType,
                         java.util.Map<String, Object> properties) {
            this.id            = id;
            this.name          = name;
            this.description   = description;
            this.triggerClicks = triggerClicks;
            this.windowMs      = windowMs;
            this.damages       = damages;
            this.animationType = animationType;
            this.particleType  = particleType;
            this.soundType     = soundType;
            this.properties    = properties;
        }

        public String       getId()            { return id; }
        public String       getName()          { return name; }
        public String       getDescription()   { return description; }
        public int          getTriggerClicks() { return triggerClicks; }
        public long         getWindowMs()      { return windowMs; }
        public List<Double> getDamages()       { return damages; }
        public String       getAnimationType() { return animationType; }
        public String       getParticleType()  { return particleType; }
        public String       getSoundType()     { return soundType; }
        public double getDouble(String key, double def) {
            Object v = properties.get(key);
            return v instanceof Number n ? n.doubleValue() : def;
        }
        public int getInt(String key, int def) {
            Object v = properties.get(key);
            return v instanceof Number n ? n.intValue() : def;
        }
        public boolean getBoolean(String key, boolean def) {
            Object v = properties.get(key);
            return v instanceof Boolean b ? b : def;
        }
    }
}
