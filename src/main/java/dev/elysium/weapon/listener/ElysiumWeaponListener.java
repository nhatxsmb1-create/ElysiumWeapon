package dev.elysium.weapon.listener;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.skill.custom.FlorentinoSkill;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import dev.elysium.weapon.weapon.WeaponData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;

public class ElysiumWeaponListener implements Listener {

    private final ElysiumWeapon plugin;
    private final FlorentinoSkill florentinoSkill;

    public ElysiumWeaponListener(ElysiumWeapon plugin) { 
        this.plugin = plugin; 
        this.florentinoSkill = new FlorentinoSkill(plugin);
    }

    // ── Click Detection (Dùng Chiêu & Cập nhật Cooldown ActionBar) ───────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player player = e.getPlayer();
        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        boolean shift = player.isSneaking();
        Action action = e.getAction();

        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick  = action == Action.LEFT_CLICK_AIR  || action == Action.LEFT_CLICK_BLOCK;

        // ⚔️ Xử lý riêng cho Kiếm Florentino (Ghi nhận Cooldown lên ActionBar)
        if ("FLORENTINO_SWORD".equalsIgnoreCase(weapon.getId())) {
            
            // Chiêu 1: Thưởng Hoa (Phải chuột)
            if (rightClick && !shift) {
                e.setCancelled(true);
                
                // Kiểm tra nếu đang đếm ngược cooldown thì chặn
                if (weapon.getSkill1() != null && state.isOnCooldown(weapon.getSkill1().getId())) {
                    return;
                }
                
                // Tung chiêu & Đăng ký cooldown vào ActionBar
                boolean success = florentinoSkill.throwFlowers(player);
                if (success && weapon.getSkill1() != null) {
                    state.setCooldown(weapon.getSkill1().getId(), weapon.getSkill1().getCooldown());
                }
                return;
            } 
            
            // Ult: Tài Hoa (Shift + Phải chuột)
            else if (rightClick && shift) {
                e.setCancelled(true);
                
                var ultSkill = weapon.getSkill2() != null ? weapon.getSkill2() : weapon.getUltimate();
                
                if (ultSkill != null && state.isOnCooldown(ultSkill.getId())) {
                    return;
                }
                
                boolean success = florentinoSkill.castUltimate(player);
                if (success && ultSkill != null) {
                    state.setCooldown(ultSkill.getId(), ultSkill.getCooldown());
                }
                return;
            }
        }

        // Vũ khí thường
        if (rightClick && !shift) {
            e.setCancelled(true);
            if (weapon.getSkill1() != null) {
                plugin.getSkillEngine().executeSkill(player, weapon, weapon.getSkill1(), state);
            }
        } else if (rightClick && shift) {
            e.setCancelled(true);
            if (weapon.getSkill2() != null) {
                plugin.getSkillEngine().executeSkill(player, weapon, weapon.getSkill2(), state);
            }
        } else if (leftClick && shift) {
            e.setCancelled(true);
            if (weapon.getUltimate() != null) {
                plugin.getSkillEngine().executeSkill(player, weapon, weapon.getUltimate(), state);
            }
        }
    }

    // ── Attack & Combo Detection ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (florentinoSkill.isInternalDamage()) return;

        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;

        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        if ("FLORENTINO_SWORD".equalsIgnoreCase(weapon.getId())) {
            if (florentinoSkill.handleHitAndDash(player, target, state)) {
                return;
            }
            if (florentinoSkill.onHitDuringVortex(player, target, state)) {
                return;
            }
        }

        e.setDamage(weapon.getBaseDamage());
        handlePassive(player, weapon, target, e);

        if (weapon.getCombo() == null) return;
        int clicks = state.registerClick(weapon.getCombo().getWindowMs());

        if (clicks >= weapon.getCombo().getTriggerClicks() && !state.isComboActive()) {
            state.setComboActive(true);
            e.setCancelled(true);
            plugin.getSkillEngine().executeCombo(player, weapon, weapon.getCombo(), state);
        }
    }

    // ── Passive Handling ──────────────────────────────────────────────────────

    private void handlePassive(Player player, WeaponData weapon,
                                LivingEntity target,
                                EntityDamageByEntityEvent event) {
        if (weapon.getPassive() == null) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        switch (weapon.getPassive().getId()) {
            case "MOMENTUM" -> {
                int stacks = state.getPassiveStack("MOMENTUM");
                if (stacks > 0) event.setDamage(event.getDamage() * (1 + stacks * 0.05));
            }
            case "HEAVY_BLOW" -> {
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
                if (player.getVelocity().lengthSquared() < 0.01) {
                    double bonus = weapon.getPassive().getDouble("damage-bonus", 0.4);
                    event.setDamage(event.getDamage() * (1 + bonus));
                    player.sendActionBar(color("&a🎯 Eagle Eye! +40% Dame"));
                }
            }
            case "LETHAL_TEMPO" -> {
                double backstabMult = weapon.getPassive().getDouble("backstab-multiplier", 1.5);
                double angle = getAngleBehind(player, target);
                if (angle > 120) {
                    event.setDamage(event.getDamage() * backstabMult);
                    player.sendActionBar(color("&5🗡 Backstab! +50% Dame"));
                }
            }
            case "ISSEN_READY" -> {
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
        Player player = e.getPlayer();
        String newId  = plugin.getWeaponManager().getHeldWeaponId(player);
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        state.setCurrentWeapon(newId);
        state.resetCombo();
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private double getAngleBehind(Player attacker, LivingEntity target) {
        org.bukkit.util.Vector toAttacker = attacker.getLocation()
                .subtract(target.getLocation()).toVector().normalize();
        org.bukkit.util.Vector targetDir  = target.getLocation().getDirection().normalize();
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, targetDir.dot(toAttacker)))));
    }

    private String color(String s) { return s.replace("&", "\u00a7"); }
}
