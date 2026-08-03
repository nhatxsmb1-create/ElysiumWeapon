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
import org.bukkit.inventory.EquipmentSlot;

public class FlorentinoListener implements Listener {

    private final ElysiumWeapon plugin;
    private final FlorentinoSkill florentinoSkill;

    public FlorentinoListener(ElysiumWeapon plugin) {
        this.plugin = plugin;
        this.florentinoSkill = new FlorentinoSkill(plugin);
    }

    // ── Xử lý Click Chiêu 1 & Chiêu Cuối ────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onFlorentinoInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player player = e.getPlayer();
        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null || !"FLORENTINO_SWORD".equalsIgnoreCase(weapon.getId())) return;

        Action action = e.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean shift = player.isSneaking();

        if (rightClick) {
            e.setCancelled(true);
            if (!shift) {
                florentinoSkill.throwFlowers(player); // C1: Ném hoa
            } else {
                florentinoSkill.castUltimate(player); // Ulti: Tài hoa
            }
        }
    }

    // ── Xử lý Đòn Chém, Bỏ Bất Tử & Lướt Nhặt Hoa ──────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlorentinoAttack(EntityDamageByEntityEvent e) {
        // Bỏ qua nếu là dame nội bộ do Skill gây ra (tránh lặp vô tận)
        if (florentinoSkill.isInternalDamage()) return;

        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;

        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null || !"FLORENTINO_SWORD".equalsIgnoreCase(weapon.getId())) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        // FIX PVP: Ép Minecraft bỏ tick bất tử riêng cho đòn đánh của Florentino
        target.setMaximumNoDamageTicks(0);
        target.setNoDamageTicks(0);

        // 1. Kiểm tra Thưởng Kiếm (Chiêu 2)
        if (florentinoSkill.onHitDuringVortex(player, target, state, e)) {
            return;
        }

        // 2. Kiểm tra Lướt nhặt hoa (Nội tại)
        if (florentinoSkill.handleHitAndDash(player, target, state, e)) {
            return;
        }
    }
}
