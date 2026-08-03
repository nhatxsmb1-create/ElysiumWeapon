package dev.elysium.weapon.listener;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import dev.elysium.weapon.weapon.WeaponData;
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

public class ElysiumWeaponListener implements Listener {

    private final ElysiumWeapon plugin;

    public ElysiumWeaponListener(ElysiumWeapon plugin) { 
        this.plugin = plugin; 
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player player = e.getPlayer();
        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null) return;

        // 🛑 BỎ QUA FLORENTINO (Đã có FlorentinoListener xử lý riêng)
        if ("FLORENTINO_SWORD".equalsIgnoreCase(weapon.getId())) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        boolean shift = player.isSneaking();
        Action action = e.getAction();

        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick  = action == Action.LEFT_CLICK_AIR  || action == Action.LEFT_CLICK_BLOCK;

        if (rightClick && !shift) {
            e.setCancelled(true);
            if (weapon.getSkill1() != null) plugin.getSkillEngine().executeSkill(player, weapon, weapon.getSkill1(), state);
        } else if (rightClick && shift) {
            e.setCancelled(true);
            if (weapon.getSkill2() != null) plugin.getSkillEngine().executeSkill(player, weapon, weapon.getSkill2(), state);
        } else if (leftClick && shift) {
            e.setCancelled(true);
            if (weapon.getUltimate() != null) plugin.getSkillEngine().executeSkill(player, weapon, weapon.getUltimate(), state);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;

        WeaponData weapon = plugin.getWeaponManager().getHeldWeaponData(player);
        if (weapon == null) return;

        // 🛑 BỎ QUA FLORENTINO (Đã có FlorentinoListener xử lý riêng)
        if ("FLORENTINO_SWORD".equalsIgnoreCase(weapon.getId())) return;

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        e.setDamage(weapon.getBaseDamage());

        if (weapon.getCombo() == null) return;
        int clicks = state.registerClick(weapon.getCombo().getWindowMs());

        if (clicks >= weapon.getCombo().getTriggerClicks() && !state.isComboActive()) {
            state.setComboActive(true);
            e.setCancelled(true);
            plugin.getSkillEngine().executeCombo(player, weapon, weapon.getCombo(), state);
        }
    }

    @EventHandler
    public void onWeaponSwitch(PlayerItemHeldEvent e) {
        Player player = e.getPlayer();
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            String newWeaponId = plugin.getWeaponManager().getHeldWeaponId(player);
            PlayerWeaponState state = plugin.getWeaponManager().getState(player);
            state.setCurrentWeapon(newWeaponId);
            state.resetCombo();
        }, 1L);
    }
}
