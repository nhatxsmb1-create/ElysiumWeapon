package dev.elysium.weapon.skill.custom;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class FlorentinoSkill {

    private final ElysiumWeapon plugin;
    private final List<FlowerInstance> activeFlowers = new ArrayList<>();
    private final Set<UUID> ultActivePlayers = new HashSet<>();
    private boolean internalDamage = false;

    public FlorentinoSkill(ElysiumWeapon plugin) {
        this.plugin = plugin;
        startFlowerTicker();
    }

    public boolean isInternalDamage() {
        return internalDamage;
    }

    // ── Chiêu 1: Thưởng Hoa (Ném 3 Hoa) ──────────────────────────────────────

    public boolean throwFlowers(Player player) {
        Location center = player.getLocation();
        Vector dir = center.getDirection().setY(0).normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

        // Ném 3 hoa: Trái, Giữa, Phải
        spawnFlower(player, center.clone().add(dir.clone().multiply(3)).add(right.clone().multiply(2.5)));
        spawnFlower(player, center.clone().add(dir.clone().multiply(4.5)));
        spawnFlower(player, center.clone().add(dir.clone().multiply(3)).subtract(right.clone().multiply(2.5)));

        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1);

        return true;
    }

    private void spawnFlower(Player owner, Location targetLoc) {
        Location spawnLoc = targetLoc.clone();
        spawnLoc.setY(targetLoc.getWorld().getHighestBlockYAt(targetLoc) + 1.0);

        ArmorStand stand = targetLoc.getWorld().spawn(spawnLoc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);

            ItemStack rose = new ItemStack(org.bukkit.Material.POPPY);
            ItemMeta meta = rose.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(1001);
                rose.setItemMeta(meta);
            }
            as.getEquipment().setHelmet(rose);
        });

        activeFlowers.add(new FlowerInstance(owner.getUniqueId(), stand, System.currentTimeMillis() + 6000));
    }

    // ── Ult: Tài Hoa ─────────────────────────────────────────────────────────

    public boolean castUltimate(Player player) {
        UUID uuid = player.getUniqueId();
        ultActivePlayers.add(uuid);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);

        // Hết 5s thì tắt trạng thái Ult
        Bukkit.getScheduler().runTaskLater(plugin, () -> ultActivePlayers.remove(uuid), 100L);

        return true;
    }

    // ── Xử Lý Lướt & Nhặt Hoa Khi Chém ───────────────────────────────────────

    public boolean handleHitAndDash(Player player, LivingEntity target, PlayerWeaponState state) {
        FlowerInstance nearest = findNearestFlower(player);

        if (nearest != null) {
            // Teleport lướt tới hoa
            Location flowerLoc = nearest.stand.getLocation();
            Vector dashDir = flowerLoc.toVector().subtract(player.getLocation().toVector()).normalize();
            player.setVelocity(dashDir.multiply(1.2).setY(0.2));

            // Hiệu ứng nhặt hoa
            nearest.stand.remove();
            activeFlowers.remove(nearest);

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.8f);
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.5, 0), 5, 0.2, 0.2, 0.2, 0.1);

            // Tăng nội tại / Hồi chiêu 2
            state.addPassiveStack("FLORENTINO_VORTEX", 1, 60);
            return true;
        }

        return false;
    }

    public boolean onHitDuringVortex(Player player, LivingEntity target, PlayerWeaponState state) {
        int vortexStacks = state.getPassiveStack("FLORENTINO_VORTEX");
        if (vortexStacks <= 0) return false;

        state.clearPassiveStack("FLORENTINO_VORTEX");

        // Gây sát thương chuẩn & làm chậm
        internalDamage = true;
        target.damage(12.0, player);
        internalDamage = false;

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.5f);
        player.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.2);

        return true;
    }

    // ── Ticker Quản Lý Thời Gian Tồn Tại Của Hoa ─────────────────────────────

    private void startFlowerTicker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            Iterator<FlowerInstance> it = activeFlowers.iterator();

            while (it.hasNext()) {
                FlowerInstance flower = it.next();
                if (now > flower.expireTime || !flower.stand.isValid()) {
                    flower.stand.remove();
                    it.remove();
                } else {
                    flower.stand.getWorld().spawnParticle(Particle.HEART, 
                            flower.stand.getLocation().add(0, 1.2, 0), 1, 0.1, 0.1, 0.1, 0.0);
                }
            }
        }, 5L, 5L);
    }

    private FlowerInstance findNearestFlower(Player player) {
        FlowerInstance nearest = null;
        double minDistance = 4.5; // Bán kính nhặt hoa 4.5 block

        for (FlowerInstance flower : activeFlowers) {
            if (flower.ownerUuid.equals(player.getUniqueId()) && flower.stand.isValid()) {
                double dist = flower.stand.getLocation().distance(player.getLocation());
                if (dist < minDistance) {
                    minDistance = dist;
                    nearest = flower;
                }
            }
        }
        return nearest;
    }

    private static class FlowerInstance {
        final UUID ownerUuid;
        final ArmorStand stand;
        final long expireTime;

        FlowerInstance(UUID ownerUuid, ArmorStand stand, long expireTime) {
            this.ownerUuid = ownerUuid;
            this.stand = stand;
            this.expireTime = expireTime;
        }
    }
}
