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

    // Khởi tạo nhận FlorentinoSkill dùng chung instance từ ElysiumWeapon
    public FlorentinoListener(ElysiumWeapon plugin, FlorentinoSkill florentinoSkill) {
        this.plugin = plugin;
        this.florentinoSkill = florentinoSkill;
    }

    // ── Click Detection (Dùng Chiêu Florentino) ─────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player player = e.getPlayer();
        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null || !"FLORENTINO_SWORD".equalsIgnoreCase(weapon.getId())) return;

        boolean shift = player.isSneaking();
        Action action = e.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;

        if (rightClick) {
            if (!shift) {
                e.setCancelled(true);
                florentinoSkill.throwFlowers(player); // Chiêu 1
            } else {
                e.setCancelled(true);
                florentinoSkill.castUltimate(player); // Ulti
            }
        }
    }

    // ── Attack Detection (Chém / Lướt / Thưởng Kiếm) ────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        // [QUAN TRỌNG] Tránh vòng lặp lặp vô tận khi hàm target.damage() nội bộ kích hoạt Event này
        if (florentinoSkill.isInternalDamage()) return;

        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;

        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null || !"FLORENTINO_SWORD".equalsIgnoreCase(weapon.getId())) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        // 1. Lướt nhặt hoa (Nội tại)
        if (florentinoSkill.handleHitAndDash(player, target, state)) {
            e.setCancelled(true); // Hủy đòn chém tay mặc định để không bị x2 sát thương / giật combo
            return;
        }

        // 2. Thưởng Kiếm (Chiêu 2)
        if (florentinoSkill.onHitDuringVortex(player, target, state)) {
            e.setCancelled(true); // Hủy đòn chém tay mặc định
            return;
        }
    }
}
