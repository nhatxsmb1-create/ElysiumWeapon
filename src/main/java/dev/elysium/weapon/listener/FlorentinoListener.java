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
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffectType;

public class FlorentinoListener implements Listener {

    private final ElysiumWeapon plugin;
    private final FlorentinoSkill florentinoSkill;

    public FlorentinoListener(ElysiumWeapon plugin) {
        this.plugin = plugin;
        this.florentinoSkill = new FlorentinoSkill(plugin);
    }

    public FlorentinoSkill getFlorentinoSkill() {
        return florentinoSkill;
    }

    // ── XỬ LÝ CLICK SỬ DỤNG KỸ NĂNG & LƯỚT ─────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player player = e.getPlayer();
        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null || !"FLORENTINO_SWORD".equalsIgnoreCase(weapon.getId())) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        Action action = e.getAction();
        boolean shift = player.isSneaking();

        // Left Click -> Kiểm tra & Kích hoạt Lướt Kiếm Cường Hóa
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            florentinoSkill.executeDashAttack(player, state);
            return;
        }

        // Right Click -> Skill 1 hoặc Ultimate
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (rightClick) {
            e.setCancelled(true);
            if (!shift) {
                florentinoSkill.throwFlowers(player);
            } else {
                florentinoSkill.castUltimate(player);
            }
        }
    }

    // ── XỬ LÝ TÁC ĐỘNG VÀO QUÁI / PLAYER ─────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (florentinoSkill.isInternalDamage()) return;

        // Xử lý Florentino đi gây sát thương
        if (e.getDamager() instanceof Player player) {
            WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
            if (weapon != null && "FLORENTINO_SWORD".equalsIgnoreCase(weapon.getId())) {
                PlayerWeaponState state = plugin.getWeaponManager().getState(player);

                if (florentinoSkill.executeDashAttack(player, state)) {
                    e.setCancelled(true);
                    return;
                }
                if (e.getEntity() instanceof LivingEntity target) {
                    florentinoSkill.triggerVortexCombo(player, target);
                }
            }
        }

        // Xử lý Florentino bị tấn công khi đang bật Miễn Khống (Anti-Knockback)
        if (e.getEntity() instanceof Player victim) {
            PlayerWeaponState state = plugin.getWeaponManager().getState(victim);
            if (state != null && state.getPassiveStack("FLORENTINO_CC_IMMUNE") > 0) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    victim.setVelocity(victim.getVelocity().setX(0).setZ(0));
                }, 1L);
            }
        }
    }

    // ── HỆ THỐNG MIỄN KHỐNG CHẾ (CC IMMUNITY) ───────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        if (state != null && state.getPassiveStack("FLORENTINO_CC_IMMUNE") > 0) {
            if (e.getNewEffect() != null && isNegativeEffect(e.getNewEffect().getType())) {
                e.setCancelled(true); // Hủy toàn bộ hiệu ứng khống chế bất lợi
            }
        }
    }

    private boolean isNegativeEffect(PotionEffectType type) {
        return type.equals(PotionEffectType.SLOWNESS) ||
               type.equals(PotionEffectType.BLINDNESS) ||
               type.equals(PotionEffectType.POISON) ||
               type.equals(PotionEffectType.WITHER) ||
               type.equals(PotionEffectType.WEAKNESS) ||
               type.equals(PotionEffectType.LEVITATION) ||
               type.equals(PotionEffectType.NAUSEA) ||
               type.equals(PotionEffectType.DARKNESS) ||
               type.equals(PotionEffectType.UNLUCK);
    }

    // ── DỌN DẸP BỘ NHỚ KHI OUT ──────────────────────────────────────────────

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        florentinoSkill.clearPlayerData(e.getPlayer().getUniqueId());
    }
}
