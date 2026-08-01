package dev.elysium.weapon;

import dev.elysium.weapon.animation.AnimationEngine;
import dev.elysium.weapon.api.WeaponAPI;
import dev.elysium.weapon.command.WeaponAdminCommand;
import dev.elysium.weapon.command.WeaponCommand;
import dev.elysium.weapon.database.WeaponDatabase;
import dev.elysium.weapon.gui.GuiListener;
import dev.elysium.weapon.listener.DatabaseListener;
import dev.elysium.weapon.listener.WeaponListener;
import dev.elysium.weapon.mastery.WeaponMastery;
import dev.elysium.weapon.skill.SkillEngine;
import dev.elysium.weapon.util.CooldownActionbarTask;
import dev.elysium.weapon.weapon.WeaponManager;
import org.bukkit.plugin.java.JavaPlugin;

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

        // Database truoc tien
        weaponDatabase    = new WeaponDatabase(this);
        weaponDatabase.initialize();

        // Engines
        animationEngine   = new AnimationEngine(this);
        weaponManager     = new WeaponManager(this);
        skillEngine       = new SkillEngine(this, animationEngine);
        weaponMastery     = new WeaponMastery(this);
        cooldownActionbar = new CooldownActionbarTask(this);

        WeaponAPI.init(this);

        // Commands
        getCommand("weapon").setExecutor(new WeaponCommand(this));
        getCommand("weaponadmin").setExecutor(new WeaponAdminCommand(this));

        // Listeners
        getServer().getPluginManager().registerEvents(new WeaponListener(this), this);
        getServer().getPluginManager().registerEvents(new DatabaseListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        // Start cooldown actionbar
        cooldownActionbar.start();

        getLogger().info("=== ElysiumWeapon v" + getDescription().getVersion() + " enabled! ===");
        getLogger().info("Weapons: " + weaponManager.getWeaponIds().size());
    }

    @Override
    public void onDisable() {
        // Dung actionbar task
        if (cooldownActionbar != null) cooldownActionbar.stop();

        // Save tat ca player data truoc khi shutdown
        for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
            var state = weaponManager.getState(p);
            var allExp = state.getAllWeaponExp();
            if (!allExp.isEmpty()) {
                // Save dong bo khi shutdown
                for (var entry : allExp.entrySet()) {
                    weaponDatabase.saveWeaponExp(p.getUniqueId(), entry.getKey(), entry.getValue());
                }
            }
        }

        if (weaponDatabase != null) weaponDatabase.close();
        getLogger().info("ElysiumWeapon disabled.");
    }

    public static ElysiumWeapon   getInstance()         { return instance; }
    public WeaponDatabase         getWeaponDatabase()   { return weaponDatabase; }
    public WeaponManager          getWeaponManager()    { return weaponManager; }
    public AnimationEngine        getAnimationEngine()  { return animationEngine; }
    public SkillEngine            getSkillEngine()      { return skillEngine; }
    public WeaponMastery          getWeaponMastery()    { return weaponMastery; }
    public CooldownActionbarTask  getCooldownActionbar(){ return cooldownActionbar; }
}
