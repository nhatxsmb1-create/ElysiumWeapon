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

    // ── Sử dụng chiêu & Lướt kiếm ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player player = e.getPlayer();
        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null || !"FLORENTINO_SWORD".equalsIgnoreCase(weapon.getId())) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        Action action = e.getAction();
        boolean shift = player.isSneaking();

        // 1. LEFT CLICK (Chém gió/Không khí) -> Nếu có buff nhặt hoa thì kích hoạt Lướt Đánh Cường Hóa ngay!
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            florentinoSkill.executeDashAttack(player, state);
            return;
        }

        // 2. RIGHT CLICK -> Tung chiêu
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (rightClick) {
            e.setCancelled(true);
            if (!shift) {
                florentinoSkill.throwFlowers(player); // Chiêu 1
            } else {
                florentinoSkill.castUltimate(player); // Ulti
            }
        }
    }

    // ── Xử lý đòn đánh & Combo Thưởng Kiếm ─────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (florentinoSkill.isInternalDamage()) return;

        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;

        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null || !"FLORENTINO_SWORD".equalsIgnoreCase(weapon.getId())) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        // Nếu vừa nhặt hoa xong mà đập thẳng vào quái -> Ưu tiên Lướt Đánh Cường Hóa
        if (florentinoSkill.executeDashAttack(player, state)) {
            e.setCancelled(true);
            return;
        }

        // Chiêu 2: Thưởng kiếm Combo 3 nhịp
        florentinoSkill.triggerVortexCombo(player, target);
    }
}
