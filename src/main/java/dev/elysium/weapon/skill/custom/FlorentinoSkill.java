package dev.elysium.weapon.skill.custom;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Florentino Sword — cơ chế:
 *
 * PASSIVE (nội tại):
 *   Mỗi 30s → FLORENTINO_READY = true
 *   Click trái CHÉM TRÚNG mob → nếu READY → Dash xuyên qua mob → lướt đến hoa gần nhất để nhặt
 *   Nhặt hoa (Poppy) → reset cooldown nội tại ngay lập tức
 *
 * SKILL 1 (Click phải):
 *   Ném 3 bông hoa Poppy ra xung quanh player
 */
public class FlorentinoSkill {

    private final ElysiumWeapon plugin;

    // Key luu trong PlayerWeaponState passive stack
    public static final String PASSIVE_KEY = "FLORENTINO_READY";
    // Key cooldown 30s
    public static final String CD_KEY      = "FLORENTINO_PASSIVE_CD";

    // Hoa tren san: Location -> expire ms
    private final Map<UUID, List<FlowerEntry>> flowerMap = new HashMap<>();

    public FlorentinoSkill(ElysiumWeapon plugin) {
        this.plugin = plugin;
        startPassiveTimer();
    }

    // ── Passive Timer ─────────────────────────────────────────────────────────

    private void startPassiveTimer() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    PlayerWeaponState state = plugin.getWeaponManager().getState(p);
                    String wid = state.getCurrentWeapon();
                    if (!"FLORENTINO_SWORD".equals(wid)) continue;
                    if (state.isOnCooldown(CD_KEY)) continue;
                    // San sang dash
                    if (state.getPassiveStack(PASSIVE_KEY) == 0) {
                        state.addPassiveStack(PASSIVE_KEY, 1, 99999);
                        p.sendActionBar(color("&d✦ &fNoi tai san sang! &7[Click trai chem trung de Dash]"));
                        p.getWorld().playSound(p.getLocation(),
                                Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
                    }
                }
            }
        }.runTaskTimer(plugin, 600L, 600L); // 600 tick = 30s
    }

    // ── On Hit — kích hoạt Dash ───────────────────────────────────────────────

    /**
     * Gọi từ WeaponListener khi player cầm Florentino Sword đánh trúng mob.
     * Trả về true nếu đã kích hoạt dash (cancel dame gốc và thay bằng dame dash).
     */
    public boolean onHit(Player player, LivingEntity target, PlayerWeaponState state, double baseDamage) {
        if (state.getPassiveStack(PASSIVE_KEY) == 0) return false;

        // Tiêu passive
        state.clearPassiveStack(PASSIVE_KEY);
        // Set cooldown 30s
        state.setCooldown(CD_KEY, 30);

        executeDash(player, target, baseDamage);
        return true;
    }

    // ── Dash Logic ────────────────────────────────────────────────────────────

    private void executeDash(Player player, LivingEntity target, double baseDamage) {
        Location from = player.getLocation();
        Location to   = target.getLocation();

        // Vector từ player đến target
        Vector dir = to.toVector().subtract(from.toVector()).normalize();

        // Particle trail trong khi dash
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick++ >= 6) { cancel(); return; }
                player.getWorld().spawnParticle(
                    Particle.CHERRY_LEAVES,
                    player.getLocation().add(0, 1, 0),
                    8, 0.3, 0.3, 0.3, 0.05);
                player.getWorld().spawnParticle(
                    Particle.ENCHANT,
                    player.getLocation().add(0, 1, 0),
                    5, 0.2, 0.2, 0.2, 0.1);
            }
        }.runTaskTimer(plugin, 0, 1);

        // Teleport xuyên qua target (overshoot 1.5 block)
        Location landLoc = to.clone().add(dir.clone().multiply(1.5));
        landLoc.setYaw(from.getYaw());
        landLoc.setPitch(from.getPitch());
        player.teleport(landLoc);

        // Gây dame
        target.damage(baseDamage * 1.8, player);
        player.getWorld().playSound(landLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0,1,0), 3);

        player.sendActionBar(color("&d✦ &fDash!"));

        // Sau dash → tìm hoa gần nhất và lướt đến
        Bukkit.getScheduler().runTaskLater(plugin, () -> glideToFlower(player, state -> {
            // Callback khi nhặt hoa: reset cooldown ngay
        }), 3L);
    }

    // ── Glide đến hoa ────────────────────────────────────────────────────────

    private void glideToFlower(Player player, java.util.function.Consumer<PlayerWeaponState> onPickup) {
        FlowerEntry nearest = getNearestFlower(player, 15);
        if (nearest == null) return;

        Location flowerLoc = nearest.location.clone().add(0.5, 0, 0.5);
        Vector dir = flowerLoc.toVector()
                .subtract(player.getLocation().toVector()).normalize();
        dir.setY(0.1);

        // Particle trail lướt đến hoa
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick++ >= 8) {
                    cancel();
                    // Kiểm tra nhặt hoa
                    pickupFlower(player, nearest);
                    return;
                }
                player.setVelocity(dir.clone().multiply(1.2));
                player.getWorld().spawnParticle(
                    Particle.CHERRY_LEAVES,
                    player.getLocation().add(0, 0.5, 0),
                    4, 0.1, 0.1, 0.1, 0);
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ── Nhặt hoa ────────────────────────────────────────────────────────────

    private void pickupFlower(Player player, FlowerEntry entry) {
        List<FlowerEntry> list = flowerMap.get(player.getWorld().getUID());
        if (list != null) list.remove(entry);

        // Xóa item trên sàn
        for (Entity e : player.getWorld().getNearbyEntities(entry.location.clone().add(0.5,0,0.5), 1, 1, 1)) {
            if (e instanceof Item item && item.getItemStack().getType() == Material.POPPY) {
                item.remove();
                break;
            }
        }

        // Reset cooldown passive ngay lập tức
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        state.clearPassiveStack(PASSIVE_KEY);
        // Xóa cooldown để passive timer set lại ngay
        state.setCooldown(CD_KEY, 0);
        // Set passive ready ngay
        state.addPassiveStack(PASSIVE_KEY, 1, 99999);

        player.sendActionBar(color("&d✦ &fNhat hoa! Noi tai san sang lai!"));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.6f);
        player.getWorld().spawnParticle(Particle.CHERRY_LEAVES,
                player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
    }

    // ── Skill 1: Ném 3 hoa ───────────────────────────────────────────────────

    public void throwFlowers(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation();

        // Ném 3 hoa theo 3 hướng cách đều 120 độ
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(i * 120 + player.getLocation().getYaw() + 90);
            double dist  = 2.5 + Math.random() * 1.5;
            double x     = center.getX() + dist * Math.cos(angle);
            double z     = center.getZ() + dist * Math.sin(angle);
            Location land = new Location(world, x, center.getY(), z);

            // Tìm đất dưới
            land = findGround(land);

            // Spawn item hoa
            Item flower = world.dropItem(land.clone().add(0, 0.5, 0), new ItemStack(Material.POPPY));
            flower.setPickupDelay(Integer.MAX_VALUE); // Player ko nhặt tay, chỉ dash đến mới nhặt
            flower.setVelocity(new Vector(0, 0, 0));
            flower.setGlowing(true);
            flower.setCustomName(color("&d✦ Hoa Florentino"));
            flower.setCustomNameVisible(true);

            // Lưu vào map
            flowerMap.computeIfAbsent(world.getUID(), k -> new ArrayList<>())
                     .add(new FlowerEntry(land, System.currentTimeMillis() + 20_000));

            // Particle tại điểm rơi
            world.spawnParticle(Particle.CHERRY_LEAVES, land.clone().add(0,0.5,0), 15, 0.3, 0.3, 0.3, 0.02);
        }

        world.playSound(center, Sound.BLOCK_CHERRY_LEAVES_PLACE, 1f, 1.2f);
        player.sendActionBar(color("&d✦ &fNem 3 hoa! Dash den de nhat!"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private FlowerEntry getNearestFlower(Player player, double maxDist) {
        List<FlowerEntry> list = flowerMap.get(player.getWorld().getUID());
        if (list == null || list.isEmpty()) return null;

        long now = System.currentTimeMillis();
        list.removeIf(f -> now > f.expireMs); // Dọn hoa hết hạn

        FlowerEntry nearest = null;
        double minDist = maxDist * maxDist;
        for (FlowerEntry f : list) {
            double d = player.getLocation().distanceSquared(f.location.clone().add(0.5, 0, 0.5));
            if (d < minDist) { minDist = d; nearest = f; }
        }
        return nearest;
    }

    private Location findGround(Location loc) {
        Location l = loc.clone();
        for (int i = 0; i < 5; i++) {
            if (!l.getBlock().getType().isAir()) { l.add(0, 1, 0); break; }
            l.subtract(0, 1, 0);
        }
        return l;
    }

    private String color(String s) { return s.replace("&", "\u00a7"); }

    // ── Inner class ───────────────────────────────────────────────────────────

    private static class FlowerEntry {
        final Location location;
        final long     expireMs;
        FlowerEntry(Location l, long e) { location = l; expireMs = e; }
    }
}
