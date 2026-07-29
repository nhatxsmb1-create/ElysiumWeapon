package dev.elysium.weapon;

import dev.elysium.weapon.animation.AnimationEngine;
import dev.elysium.weapon.api.WeaponAPI;
import dev.elysium.weapon.command.WeaponAdminCommand;
import dev.elysium.weapon.command.WeaponCommand;
import dev.elysium.weapon.listener.WeaponListener;
import dev.elysium.weapon.mastery.WeaponMastery;
import dev.elysium.weapon.skill.SkillEngine;
import dev.elysium.weapon.weapon.WeaponManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ElysiumWeapon extends JavaPlugin {

    private static ElysiumWeapon instance;

    private WeaponManager   weaponManager;
    private AnimationEngine animationEngine;
    private SkillEngine     skillEngine;
    private WeaponMastery   weaponMastery;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("weapons.yml", false);

        // Init engines
        animationEngine = new AnimationEngine(this);
        weaponManager   = new WeaponManager(this);
        skillEngine     = new SkillEngine(this, animationEngine);
        weaponMastery   = new WeaponMastery(this);

        WeaponAPI.init(this);

        // Commands
        getCommand("weapon").setExecutor(new WeaponCommand(this));
        getCommand("weaponadmin").setExecutor(new WeaponAdminCommand(this));

        // Listeners
        getServer().getPluginManager().registerEvents(new WeaponListener(this), this);

        getLogger().info("=== ElysiumWeapon v" + getDescription().getVersion() + " enabled! ===");
        getLogger().info("Loaded " + weaponManager.getWeaponIds().size() + " weapon(s).");
    }

    @Override
    public void onDisable() {
        getLogger().info("ElysiumWeapon disabled.");
    }

    public static ElysiumWeapon getInstance() { return instance; }
    public WeaponManager        getWeaponManager()   { return weaponManager; }
    public AnimationEngine      getAnimationEngine() { return animationEngine; }
    public SkillEngine          getSkillEngine()     { return skillEngine; }
    public WeaponMastery        getWeaponMastery()   { return weaponMastery; }
}
