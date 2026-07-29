package dev.elysium.weapon.listener;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import dev.elysium.weapon.weapon.WeaponData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

public class WeaponListener implements Listener {

    private final ElysiumWeapon plugin;

    public WeaponListener(ElysiumWeapon plugin) { this.plugin = plugin; }

    // ── Click Detection ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player player = e.getPlayer();
        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        boolean shift = player.isSneaking();
        Action  action= e.getAction();

        // Phan loai input
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick  = action == Action.LEFT_CLICK_AIR  || action == Action.LEFT_CLICK_BLOCK;

        if (rightClick && !shift) {
            // P.Phai giu -> Skill 1
            e.setCancelled(true);
            if (weapon.getSkill1() != null) {
                plugin.getSkillEngine().executeSkill(player, weapon, weapon.getSkill1(), state);
            }
        } else if (rightClick && shift) {
            // Shift + P.Phai -> Skill 2
            e.setCancelled(true);
            if (weapon.getSkill2() != null) {
                plugin.getSkillEngine().executeSkill(player, weapon, weapon.getSkill2(), state);
            }
        } else if (leftClick && shift) {
            // Shift + P.Trai -> Ultimate
            e.setCancelled(true);
            if (weapon.getUltimate() != null) {
                plugin.getSkillEngine().executeSkill(player, weapon, weapon.getUltimate(), state);
            }
        }
    }

    // ── Combo Detection (Left Click) ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;

        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null) return;

        // Override damage voi base damage cua weapon
        e.setDamage(weapon.getBaseDamage());

        // Passive xu ly
        handlePassive(player, weapon, (org.bukkit.entity.LivingEntity) e.getEntity(), e);

        // Combo click tracking
        if (weapon.getCombo() == null) return;
        PlayerWeaponState state  = plugin.getWeaponManager().getState(player);
        int clicks = state.registerClick(weapon.getCombo().getWindowMs());

        if (clicks >= weapon.getCombo().getTriggerClicks() && !state.isComboActive()) {
            state.setComboActive(true);
            e.setCancelled(true); // Cancel normal hit, replace bang combo
            plugin.getSkillEngine().executeCombo(player, weapon, weapon.getCombo(), state);
        }
    }

    // ── Passive Handling ──────────────────────────────────────────────────────

    private void handlePassive(Player player, WeaponData weapon,
                                org.bukkit.entity.LivingEntity target,
                                EntityDamageByEntityEvent event) {
        if (weapon.getPassive() == null) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        switch (weapon.getPassive().getId()) {

            case "MOMENTUM" -> {
                // Tang 5% dame moi kill, toi da 3 stack
                int stacks = state.getPassiveStack("MOMENTUM");
                if (stacks > 0) {
                    event.setDamage(event.getDamage() * (1 + stacks * 0.05));
                }
            }

            case "HEAVY_BLOW" -> {
                // Moi 4 don danh, don ke tiep Stun
                int chargeCount = weapon.getPassive().getInt("charge-count", 4);
                int hitCount    = state.incrementHitCount();
                if (hitCount >= chargeCount) {
                    state.resetHitCount();
                    int stunDuration = weapon.getPassive().getInt("stun-duration", 30);
                    target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOWNESS, stunDuration, 10));
                    player.sendActionBar(color("&6&l⚡ Heavy Blow!"));
                }
            }

            case "FROSTBITE" -> {
                // 20% Chill, 3 Chill = Freeze
                double chillChance = weapon.getPassive().getDouble("chill-chance", 0.2);
                if (Math.random() < chillChance) {
                    int freezeStacks = state.addPassiveStack("FROSTBITE", 3, 100);
                    target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOWNESS, 20, 0));
                    if (freezeStacks >= 3) {
                        state.clearPassiveStack("FROSTBITE");
                        int freezeDuration = weapon.getPassive().getInt("freeze-duration", 40);
                        target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.SLOWNESS, freezeDuration, 10));
                        player.sendActionBar(color("&b❄ Freeze!"));
                    }
                }
            }

            case "EAGLE_EYE" -> {
                // Dung yen 1.5s tang dame 40%
                long standStillTicks = weapon.getPassive().getInt("stand-still-time", 30);
                // Check velocity gan = 0
                if (player.getVelocity().lengthSquared() < 0.01) {
                    double bonus = weapon.getPassive().getDouble("damage-bonus", 0.4);
                    event.setDamage(event.getDamage() * (1 + bonus));
                    player.sendActionBar(color("&a🎯 Eagle Eye! +40% Dame"));
                }
            }

            case "LETHAL_TEMPO" -> {
                // Backstab: danh tu phia sau tang 50%
                double backstabMult = weapon.getPassive().getDouble("backstab-multiplier", 1.5);
                double angle = getAngleBehind(player, target);
                if (angle > 120) { // Phia sau
                    event.setDamage(event.getDamage() * backstabMult);
                    player.sendActionBar(color("&5🗡 Backstab! +50% Dame"));
                }
            }

            case "ISSEN_READY" -> {
                // Lan danh dac biet sau ISSEN ultimate
                if (state.getPassiveStack("ISSEN_READY") > 0) {
                    double dmgMult = 6.0;
                    event.setDamage(weapon.getBaseDamage() * dmgMult);
                    state.clearPassiveStack("ISSEN_READY");
                    player.sendActionBar(color("&5&l一閃 ISSEN!"));
                }
            }
        }
    }

    // ── Weapon Switch ─────────────────────────────────────────────────────────

    @EventHandler
    public void onWeaponSwitch(PlayerItemHeldEvent e) {
        Player    player = e.getPlayer();
        String    newId  = plugin.getWeaponManager().getHeldWeaponId(player);
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        state.setCurrentWeapon(newId);
        state.resetCombo();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getWeaponManager().removeState(e.getPlayer().getUniqueId());
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private double getAngleBehind(Player attacker, org.bukkit.entity.LivingEntity target) {
        org.bukkit.util.Vector toAttacker = attacker.getLocation()
                .subtract(target.getLocation()).toVector().normalize();
        org.bukkit.util.Vector targetDir  = target.getLocation().getDirection().normalize();
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, targetDir.dot(toAttacker)))));
    }

    private String color(String s) { return s.replace("&", "\u00a7"); }
}
