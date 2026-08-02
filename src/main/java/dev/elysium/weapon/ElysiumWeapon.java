package dev.elysium.weapon;

import dev.elysium.weapon.animation.AnimationEngine;
import dev.elysium.weapon.api.WeaponAPI;
import dev.elysium.weapon.command.WeaponAdminCommand;
import dev.elysium.weapon.command.WeaponCommand;
import dev.elysium.weapon.database.WeaponDatabase;
import dev.elysium.weapon.gui.GuiListener;
import dev.elysium.weapon.listener.ElysiumDatabaseListener;
import dev.elysium.weapon.listener.ElysiumWeaponListener;
import dev.elysium.weapon.mastery.WeaponMastery;
import dev.elysium.weapon.skill.SkillEngine;
import dev.elysium.weapon.util.CooldownActionbarTask;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import dev.elysium.weapon.weapon.WeaponManager;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class ElysiumWeapon extends JavaPlugin {

    private static ElysiumWeapon instance;

    private WeaponDatabase        weaponDatabase;
    private WeaponManager         weaponManager;
    private AnimationEngine       animationEngine;
    private SkillEngine           skillEngine;
    private WeaponMastery         weaponMastery;
    private CooldownActionbarTask cooldownActionbar;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("weapons.yml", false);

        weaponDatabase    = new WeaponDatabase(this);
        weaponDatabase.initialize();

        animationEngine   = new AnimationEngine(this);
        weaponManager     = new WeaponManager(this);
        skillEngine       = new SkillEngine(this, animationEngine);
        weaponMastery     = new WeaponMastery(this);
        cooldownActionbar = new CooldownActionbarTask(this);

        WeaponAPI.init(this);

        getCommand("weapon").setExecutor(new WeaponCommand(this));
        getCommand("weaponadmin").setExecutor(new WeaponAdminCommand(this));

        getServer().getPluginManager().registerEvents(new ElysiumWeaponListener(this), this);
        getServer().getPluginManager().registerEvents(new ElysiumDatabaseListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        cooldownActionbar.start();

        // 🚨 TỰ ĐỘNG DỌN DẸP ARMORSTAND KẸT KHI START/RELOAD PLUGIN 🚨
        cleanupStuckHolograms();

        getLogger().info("=== ElysiumWeapon v" + getDescription().getVersion() + " enabled! ===");
        getLogger().info("Weapons: " + weaponManager.getWeaponIds().size());
    }

    @Override
    public void onDisable() {
        if (cooldownActionbar != null) cooldownActionbar.stop();

        // Dọn dẹp Hologram kẹt trước khi shutdown
        cleanupStuckHolograms();

        if (weaponDatabase != null) {
            for (Player p : getServer().getOnlinePlayers()) {
                PlayerWeaponState state = weaponManager.getState(p);
                Map<String, Long> allExp = state.getAllWeaponExp();
                if (!allExp.isEmpty()) {
                    weaponDatabase.saveAllWeaponExpSync(p.getUniqueId(), allExp);
                }
            }
            weaponDatabase.close();
        }
        getLogger().info("ElysiumWeapon disabled.");
    }

    /**
     * Hàm quét và xoá sạch toàn bộ ArmorStand "Marked" bị kẹt trong các World
     */
    private void cleanupStuckHolograms() {
        int removedCount = 0;
        for (World world : getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ArmorStand armorStand) {
                    if (armorStand.getCustomName() != null && armorStand.getCustomName().contains("Marked")) {
                        armorStand.remove();
                        removedCount++;
                    }
                }
            }
        }
        if (removedCount > 0) {
            getLogger().info("[CleanUp] Đã dọn dẹp thành công " + removedCount + " ArmorStand 'Marked' bị kẹt!");
        }
    }

    public static ElysiumWeapon   getInstance()         { return instance; }
    public WeaponDatabase         getWeaponDatabase()   { return weaponDatabase; }
    public WeaponManager          getWeaponManager()    { return weaponManager; }
    public AnimationEngine        getAnimationEngine()  { return animationEngine; }
    public SkillEngine            getSkillEngine()      { return skillEngine; }
    public WeaponMastery          getWeaponMastery()    { return weaponMastery; }
    public CooldownActionbarTask  getCooldownActionbar(){ return cooldownActionbar; }
}
