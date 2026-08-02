package dev.elysium.weapon.weapon;

import dev.elysium.weapon.ElysiumWeapon;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class WeaponManager {

    private final ElysiumWeapon plugin;

    private final Map<String, WeaponData>       weaponDataMap  = new HashMap<>();
    private final Map<UUID, PlayerWeaponState>  playerStates   = new HashMap<>();

    // NBT key de nhan biet vu khi Elysium
    public static final String WEAPON_ID_KEY = "elysium_weapon_id";

    public WeaponManager(ElysiumWeapon plugin) {
        this.plugin = plugin;
        loadWeapons();
    }

    // ── Load Config ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void loadWeapons() {
        File f = new File(plugin.getDataFolder(), "weapons.yml");
        if (!f.exists()) plugin.saveResource("weapons.yml", false);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);

        ConfigurationSection root = cfg.getConfigurationSection("weapons");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection w = root.getConfigurationSection(id);
            if (w == null) continue;

            WeaponData.WeaponType type;
            try { type = WeaponData.WeaponType.valueOf(w.getString("type", "SWORD")); }
            catch (Exception e) { type = WeaponData.WeaponType.SWORD; }

            WeaponData.ClassAffinity affinity;
            try { affinity = WeaponData.ClassAffinity.valueOf(w.getString("class-affinity", "NONE")); }
            catch (Exception e) { affinity = WeaponData.ClassAffinity.NONE; }

            WeaponData.PassiveData passive = loadPassive(w.getConfigurationSection("passive"));
            WeaponData.SkillData   skill1  = loadSkill(w.getConfigurationSection("skill1"));
            WeaponData.SkillData   skill2  = loadSkill(w.getConfigurationSection("skill2"));
            WeaponData.SkillData   ultimate= loadSkill(w.getConfigurationSection("ultimate"));
            WeaponData.ComboData   combo   = loadCombo(w.getConfigurationSection("combo"));

            weaponDataMap.put(id, new WeaponData(
                    id,
                    w.getString("display-name", id),
                    type, affinity,
                    w.getString("material", "IRON_SWORD"),
                    w.getInt("model-data", 0),
                    w.getStringList("lore"),
                    w.getDouble("base-damage", 10),
                    w.getDouble("base-speed", 1.0),
                    w.getInt("mana-cost-skill1", 20),
                    w.getInt("mana-cost-skill2", 30),
                    w.getInt("mana-cost-ultimate", 60),
                    passive, skill1, skill2, ultimate, combo
            ));
        }
        plugin.getLogger().info("Loaded " + weaponDataMap.size() + " weapon(s).");
    }

    private WeaponData.PassiveData loadPassive(ConfigurationSection s) {
        if (s == null) return null;
        Map<String, Object> props = new HashMap<>(s.getValues(false));
        return new WeaponData.PassiveData(
                s.getString("id", "UNKNOWN"),
                s.getString("description", ""),
                props
        );
    }

    private WeaponData.SkillData loadSkill(ConfigurationSection s) {
        if (s == null) return null;
        Map<String, Object> props = new HashMap<>(s.getValues(false));
        return new WeaponData.SkillData(
                s.getString("id", "UNKNOWN"),
                s.getString("name", ""),
                s.getString("description", ""),
                s.getInt("cooldown", 10),
                s.getString("animation", "NONE"),
                s.getString("particle", "CRIT"),
                s.getString("sound", "ENTITY_PLAYER_ATTACK_CRIT"),
                props
        );
    }

    @SuppressWarnings("unchecked")
    private WeaponData.ComboData loadCombo(ConfigurationSection s) {
        if (s == null) return null;
        List<Double> damages = new ArrayList<>();
        for (Object o : s.getList("damages", new ArrayList<>())) {
            if (o instanceof Number n) damages.add(n.doubleValue());
        }
        Map<String, Object> props = new HashMap<>(s.getValues(false));
        return new WeaponData.ComboData(
                s.getString("id", "UNKNOWN"),
                s.getString("name", ""),
                s.getString("description", ""),
                s.getInt("trigger-clicks", 3),
                s.getLong("window", 1500),
                damages,
                s.getString("animation", "NONE"),
                s.getString("particle", "CRIT"),
                s.getString("sound", "ENTITY_PLAYER_ATTACK_CRIT"),
                props
        );
    }

    // ── Give Weapon ───────────────────────────────────────────────────────────

    public ItemStack createWeaponItem(String weaponId) {
        WeaponData data = weaponDataMap.get(weaponId);
        if (data == null) return null;

        Material mat;
        try { mat = Material.valueOf(data.getMaterial()); }
        catch (Exception e) { mat = Material.IRON_SWORD; }

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();

        meta.setDisplayName(color(data.getDisplayName()));
        meta.setCustomModelData(data.getModelData());

        // Lore
        List<String> lore = new ArrayList<>();
        lore.add(color("&8" + data.getType().name() + " | " + data.getAffinity().name()));
        lore.add("");
        data.getLore().forEach(l -> lore.add(color(l)));
        lore.add("");
        lore.add(color("&7Dame: &f" + data.getBaseDamage()));
        if (data.getPassive() != null) {
            lore.add("");
            lore.add(color("&6[Noi Tai] &f" + data.getPassive().getDescription()));
        }
        lore.add("");
        if (data.getSkill1() != null)
            lore.add(color("&b[P.Phải giữ] &f" + data.getSkill1().getName() + " &7- " + data.getSkill1().getDescription()));
        if (data.getSkill2() != null)
            lore.add(color("&b[Shift+P.Phải] &f" + data.getSkill2().getName() + " &7- " + data.getSkill2().getDescription()));
        if (data.getUltimate() != null)
            lore.add(color("&c[Shift+P.Trái] &f" + data.getUltimate().getName() + " &7- " + data.getUltimate().getDescription()));
        if (data.getCombo() != null)
            lore.add(color("&e[Combo x" + data.getCombo().getTriggerClicks() + "] &f" + data.getCombo().getName()));
        lore.add("");
        lore.add(color("&8ID: " + weaponId));

        meta.setLore(lore);

        // NBT tag weapon ID
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, WEAPON_ID_KEY),
                org.bukkit.persistence.PersistentDataType.STRING,
                weaponId
        );

        // Vo hieu hoa damage mac dinh cua Bukkit (ElysiumCombat xu ly)
        meta.addAttributeModifier(
                org.bukkit.attribute.Attribute.ATTACK_DAMAGE,
                new org.bukkit.attribute.AttributeModifier(
                        new org.bukkit.NamespacedKey(plugin, "weapon_dmg"),
                        0,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                        org.bukkit.inventory.EquipmentSlotGroup.MAINHAND
                )
        );

        item.setItemMeta(meta);
        return item;
    }

    /** Tao weapon item co hien thi Mastery cho player cu the */
    public ItemStack createWeaponItemForPlayer(String weaponId, Player player) {
        ItemStack item = createWeaponItem(weaponId);
        if (item == null || player == null) return item;

        try {
            int level    = plugin.getWeaponMastery().getWeaponLevel(player, weaponId);
            long totalExp= getState(player).getWeaponExp(weaponId);
            long expNext = plugin.getWeaponMastery().getExpForNextLevel(level);
            long expCur  = totalExp - plugin.getWeaponMastery().getExpForLevel(level);
            double pct   = expNext > 0 ? (expCur / (double) expNext) * 100.0 : 100.0;

            // Lay cooldown modifier theo mastery
            int s1Cd = plugin.getWeaponMastery().getCooldownModifier(player, weaponId, "SKILL1");
            int s2Cd = plugin.getWeaponMastery().getCooldownModifier(player, weaponId, "SKILL2");
            int ultCd= plugin.getWeaponMastery().getCooldownModifier(player, weaponId, "ULTIMATE");
            double dmgBonus = plugin.getWeaponMastery().getDamageBonus(player, weaponId, "SKILL1");

            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            java.util.List<String> lore = meta.getLore();
            if (lore == null) lore = new java.util.ArrayList<>();

            // Xoa dong ID cu
            lore.removeIf(l -> l.contains("ID:") || l.contains("Mastery") || l.contains("EXP:") || l.contains("──"));

            // Them Mastery section
            lore.add(color("&8──────────────────"));
            lore.add(color("&6✦ Mastery Level: &e" + level + "&7/50"));
            lore.add(buildExpBar(expCur, expNext, pct));

            // Unlock bonus hien tai
            if (s1Cd < 0)
                lore.add(color("&a▸ Skill1 CD: &f" + Math.abs(s1Cd) + "s giam"));
            if (s2Cd < 0)
                lore.add(color("&a▸ Skill2 CD: &f" + Math.abs(s2Cd) + "s giam"));
            if (ultCd < 0)
                lore.add(color("&a▸ Ultimate CD: &f" + Math.abs(ultCd) + "s giam"));
            if (dmgBonus > 1.0)
                lore.add(color("&a▸ Damage Bonus: &f+" + String.format("%.0f%%", (dmgBonus - 1.0) * 100)));

            // Next unlock
            String nextUnlock = getNextUnlockHint(level);
            if (nextUnlock != null)
                lore.add(color("&7Next unlock: " + nextUnlock));

            lore.add(color("&8ID: " + weaponId));

            meta.setLore(lore);
            item.setItemMeta(meta);
        } catch (Exception ignored) {}

        return item;
    }

    private String buildExpBar(long cur, long max, double pct) {
        int bars   = 15;
        int filled = (int) (pct / 100.0 * bars);
        StringBuilder bar = new StringBuilder(color("&7EXP: &6["));
        for (int i = 0; i < bars; i++) {
            bar.append(i < filled ? color("&e|") : color("&8|"));
        }
        bar.append(color("&6] &f" + cur + "/" + max));
        return bar.toString();
    }

    private String getNextUnlockHint(int level) {
        if (level < 5)  return color("&eLv.5 &7- Skill1 CD -1s");
        if (level < 10) return color("&eLv.10 &7- Skill1 Damage +20%");
        if (level < 15) return color("&eLv.15 &7- Skill2 CD -2s");
        if (level < 20) return color("&eLv.20 &7- Skill2 Effect+");
        if (level < 25) return color("&eLv.25 &7- Ultimate CD -5s");
        if (level < 30) return color("&eLv.30 &7- Ultimate Damage +30%");
        if (level < 35) return color("&eLv.35 &7- Passive Tier 2");
        if (level < 40) return color("&eLv.40 &7- Basic Skin");
        if (level < 45) return color("&eLv.45 &7- Kill Effect");
        if (level < 50) return color("&eLv.50 &7- Mastery Skin");
        return color("&6✦ MASTERY COMPLETE!");
    }

    public void giveWeapon(Player player, String weaponId) {
        ItemStack item = createWeaponItemForPlayer(weaponId, player);
        if (item == null) { player.sendMessage(color("&cWeapon khong ton tai: " + weaponId)); return; }
        player.getInventory().addItem(item);
        player.sendMessage(color("&aNhan duoc: " + weaponDataMap.get(weaponId).getDisplayName()));
    }

    // ── Detect Held Weapon ────────────────────────────────────────────────────

    public String getHeldWeaponId(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        var key = new org.bukkit.NamespacedKey(plugin, WEAPON_ID_KEY);
        if (!pdc.has(key)) return null;
        return pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);
    }

    public WeaponData getHeldWeaponData(Player player) {
        String id = getHeldWeaponId(player);
        return id != null ? weaponDataMap.get(id) : null;
    }

    // ── Player State ──────────────────────────────────────────────────────────

    public PlayerWeaponState getState(Player player) {
        return playerStates.computeIfAbsent(player.getUniqueId(),
                uuid -> new PlayerWeaponState(uuid));
    }

    public void removeState(UUID uuid) { playerStates.remove(uuid); }

    // ── Getters ───────────────────────────────────────────────────────────────

    public WeaponData           getWeaponData(String id) { return weaponDataMap.get(id); }
    public Set<String>          getWeaponIds()           { return weaponDataMap.keySet(); }
    public Map<String, WeaponData> getAllWeapons()        { return Collections.unmodifiableMap(weaponDataMap); }

    private String color(String s) { return s.replace("&", "\u00a7"); }
}
