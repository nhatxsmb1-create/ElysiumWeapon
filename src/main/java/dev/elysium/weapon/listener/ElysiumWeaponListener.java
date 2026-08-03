package dev.elysium.weapon.listener;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.skill.custom.FlorentinoSkill;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import dev.elysium.weapon.weapon.WeaponData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class ElysiumWeaponListener implements Listener {

    private final ElysiumWeapon plugin;
    private final FlorentinoSkill florentinoSkill;

    public ElysiumWeaponListener(ElysiumWeapon plugin) { 
        this.plugin = plugin; 
        this.florentinoSkill = new FlorentinoSkill(plugin);
    }

    // ── Click Detection (Dùng Chiêu) ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player player = e.getPlayer();
        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        boolean shift  = player.isSneaking();
        Action action  = e.getAction();

        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick  = action == Action.LEFT_CLICK_AIR  || action == Action.LEFT_CLICK_BLOCK;

        // ⚔️ Xử lý riêng cho Kiếm Florentino
        if ("FLORENTINO_SWORD".equalsIgnoreCase(weapon.getId())) {
            if (rightClick && !shift) {
                e.setCancelled(true);
                florentinoSkill.throwFlowers(player);
                return;
            } else if (rightClick && shift) {
                e.setCancelled(true);
                florentinoSkill.castUltimate(player);
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (florentinoSkill.isInternalDamage()) return;

        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;

        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        // ⚔️ Xử lý riêng cho Kiếm Florentino
        if ("FLORENTINO_SWORD".equalsIgnoreCase(weapon.getId())) {
            
            // Xóa i-frame bất tử PvP ngay tick tiếp theo để chém combo không bị sượng
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (target.isValid()) target.setNoDamageTicks(0);
            });

            // 1. Kiểm tra Thưởng Kiếm (Skill 2)
            if (florentinoSkill.onHitDuringVortex(player, target, state, e)) {
                return;
            }

            // 2. Kiểm tra Lướt nhặt hoa
            if (florentinoSkill.handleHitAndDash(player, target, state, e)) {
                return;
            }
        }

        // Thiết lập dame căn bản & tính toán Nội tại cho vũ khí thường
        e.setDamage(weapon.getBaseDamage());
        handlePassive(player, weapon, target, e);

        // Xử lý hệ thống Combo Đòn đánh
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
        WeaponData.PassiveData passive = weapon.getPassive();

        switch (passive.getId()) {
            case "MOMENTUM" -> {
                int stacks = state.getPassiveStack("MOMENTUM");
                double perStack = passive.getDouble("damage-per-stack", 0.05);
                if (stacks > 0) {
                    event.setDamage(event.getDamage() * (1.0 + (stacks * perStack)));
                }
            }
            case "HEAVY_BLOW" -> {
                int chargeCount = passive.getInt("charge-count", 4);
                int hitCount    = state.incrementHitCount();
                if (hitCount >= chargeCount) {
                    state.resetHitCount();
                    int stunDuration = passive.getInt("stun-duration", 30);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, stunDuration, 10));
                    player.sendActionBar(color("&6&l⚡ Heavy Blow!"));
                }
            }
            case "FROSTBITE" -> {
                double chillChance = passive.getDouble("chill-chance", 0.20);
                if (Math.random() < chillChance) {
                    int freezeStacks = state.addPassiveStack("FROSTBITE", 3, 100);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0));
                    if (freezeStacks >= 3) {
                        state.clearPassiveStack("FROSTBITE");
                        int freezeDuration = passive.getInt("freeze-duration", 40);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeDuration, 10));
                        player.sendActionBar(color("&b❄ Freeze!"));
                    }
                }
            }
            case "EAGLE_EYE" -> {
                if (player.getVelocity().lengthSquared() < 0.01) {
                    double bonus = passive.getDouble("damage-bonus", 0.40);
                    event.setDamage(event.getDamage() * (1.0 + bonus));
                    player.sendActionBar(color("&a🎯 Eagle Eye! +" + (int)(bonus * 100) + "% Dame"));
                }
            }
            case "LETHAL_TEMPO" -> {
                double backstabMult = passive.getDouble("backstab-multiplier", 1.50);
                double angle = getAngleBehind(player, target);
                if (angle > 120) {
                    event.setDamage(event.getDamage() * backstabMult);
                    player.sendActionBar(color("&5🗡 Backstab! +" + (int)((backstabMult - 1.0) * 100) + "% Dame"));
                }
            }
            case "ISSEN_READY" -> {
                if (state.getPassiveStack("ISSEN_READY") > 0) {
                    double dmgMult = passive.getDouble("damage-multiplier", 6.0);
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

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            
            String newWeaponId = plugin.getWeaponManager().getHeldWeaponId(player);
            PlayerWeaponState state = plugin.getWeaponManager().getState(player);
            
            state.setCurrentWeapon(newWeaponId);
            state.resetCombo();

            if (newWeaponId != null) {
                plugin.getWeaponMastery().refreshWeaponLore(player, newWeaponId);
            }
        }, 1L);
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private double getAngleBehind(Player attacker, LivingEntity target) {
        Vector toAttacker = attacker.getLocation().subtract(target.getLocation()).toVector().normalize();
        Vector targetDir  = target.getLocation().getDirection().normalize();
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, targetDir.dot(toAttacker)))));
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
}
