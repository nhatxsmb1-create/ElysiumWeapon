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

public class FlorentinoSkill {

    private final ElysiumWeapon plugin;

    public static final String PASSIVE_KEY = "FLORENTINO_READY";
    public static final String CD_KEY      = "FLORENTINO_PASSIVE_CD";
    public static final int    PASSIVE_CD  = 20; // giay

    // World UID -> danh sach hoa tren san
    private final Map<UUID, List<FlowerEntry>> flowerMap = new HashMap<>();

    public FlorentinoSkill(ElysiumWeapon plugin) {
        this.plugin = plugin;
        startPassiveTimer();
    }

    // ── Passive Timer: moi 20s set ready ────────────────────────────────────

    private void startPassiveTimer() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    PlayerWeaponState state = plugin.getWeaponManager().getState(p);
                    if (!"FLORENTINO_SWORD".equals(state.getCurrentWeapon())) continue;
                    if (state.isOnCooldown(CD_KEY)) continue;
                    if (state.getPassiveStack(PASSIVE_KEY) > 0) continue;

                    state.addPassiveStack(PASSIVE_KEY, 1, 99999);
                    p.sendActionBar(color("&d✦ &fNoi tai san sang! &7[Click trai de Dash]"));
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
                }
            }
        }.runTaskTimer(plugin, 400L, 400L); // 400 tick = 20s
    }

    // ── Skill 1: Ném hoa trúng mob ──────────────────────────────────────────

    public void throwFlowers(Player player) {
        World world = player.getWorld();
        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();

        // Bay thang ra truoc 5 block, moi 0.5 block kiem tra trung mob
        new BukkitRunnable() {
            double dist = 0;
            @Override public void run() {
                dist += 0.5;
                Location cur = start.clone().add(dir.clone().multiply(dist));

                // Particle projectile
                world.spawnParticle(Particle.CHERRY_LEAVES, cur, 3, 0.1, 0.1, 0.1, 0.01);

                // Kiem tra trung mob trong vong 1 block
                LivingEntity hit = null;
                for (Entity e : world.getNearbyEntities(cur, 1, 1, 1)) {
                    if (e instanceof LivingEntity le && !(le instanceof Player)
                            && !e.equals(player)) {
                        hit = le;
                        break;
                    }
                }

                if (hit != null || dist >= 5) {
                    cancel();
                    if (hit == null) return; // Bay het 5 block ko trung ai

                    Location center = hit.getLocation();

                    // Slow 2 giay
                    hit.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, 40, 1, false, true, true));

                    // Spawn 3 hoa xung quanh vi tri trung
                    for (int i = 0; i < 3; i++) {
                        double angle = Math.toRadians(i * 120.0);
                        double ox = 1.5 * Math.cos(angle);
                        double oz = 1.5 * Math.sin(angle);
                        Location flowerLoc = findGround(
                            new Location(world, center.getX() + ox, center.getY(), center.getZ() + oz));

                        Item flower = world.dropItem(
                            flowerLoc.clone().add(0, 0.3, 0),
                            new ItemStack(Material.POPPY));
                        flower.setPickupDelay(Integer.MAX_VALUE);
                        flower.setVelocity(new Vector(0, 0, 0));
                        flower.setGlowing(true);
                        flower.setCustomName(color("&d✦ Hoa Florentino"));
                        flower.setCustomNameVisible(true);

                        world.spawnParticle(Particle.CHERRY_LEAVES,
                            flower.getLocation(), 12, 0.3, 0.3, 0.3, 0.02);

                        long expireMs = System.currentTimeMillis() + 10_000L;
                        flowerMap.computeIfAbsent(world.getUID(), k -> new ArrayList<>())
                                 .add(new FlowerEntry(flower.getEntityId(), flowerLoc, expireMs));

                        final int eid = flower.getEntityId();
                        Bukkit.getScheduler().runTaskLater(plugin, () ->
                            removeFlowerById(world, eid), 200L);
                    }

                    world.playSound(center, Sound.BLOCK_CHERRY_LEAVES_PLACE, 1f, 1.2f);
                    world.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0,1,0), 5);
                    player.sendActionBar(color("&d✦ &fTrung! 3 hoa xuat hien!"));
                }
            }
        }.runTaskTimer(plugin, 0, 1); // Moi 1 tick chay 0.5 block
    }

    // ── Passive: Click trai co noi tai → Dash den hoa gan nhat ─────────────

    public boolean onLeftClick(Player player, PlayerWeaponState state) {
        if (state.getPassiveStack(PASSIVE_KEY) == 0) return false;

        FlowerEntry nearest = getNearestFlower(player, 20);
        if (nearest == null) return false;

        // Tieu passive + set CD
        state.clearPassiveStack(PASSIVE_KEY);
        state.setCooldown(CD_KEY, PASSIVE_CD);

        executeDash(player, nearest, state);
        return true;
    }

    // ── Dash den hoa ────────────────────────────────────────────────────────

    private void executeDash(Player player, FlowerEntry target, PlayerWeaponState state) {
        Location flowerLoc = target.location.clone().add(0.5, 0, 0.5);
        Vector dir = flowerLoc.toVector()
            .subtract(player.getLocation().toVector())
            .normalize();
        dir.setY(0.15);

        // Particle trail
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick++ >= 7) { cancel(); return; }
                player.getWorld().spawnParticle(
                    Particle.CHERRY_LEAVES,
                    player.getLocation().add(0, 1, 0),
                    6, 0.2, 0.2, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 0, 1);

        // Teleport gap den hoa
        Location land = flowerLoc.clone();
        land.setYaw(player.getLocation().getYaw());
        land.setPitch(player.getLocation().getPitch());
        player.teleport(land);

        // Hieu ung khi den noi
        player.getWorld().playSound(land, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);
        player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, land.clone().add(0,1,0),
            20, 0.5, 0.5, 0.5, 0.03);

        // Nhat hoa
        pickupFlower(player, target, state);
    }

    // ── Nhat hoa → reset passive ────────────────────────────────────────────

    private void pickupFlower(Player player, FlowerEntry entry, PlayerWeaponState state) {
        removeFlowerById(player.getWorld(), entry.entityId);

        // Reset passive ngay
        state.setCooldown(CD_KEY, 0);
        state.addPassiveStack(PASSIVE_KEY, 1, 99999);

        player.sendActionBar(color("&d✦ &fNhat hoa! Noi tai san sang!"));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.6f);
        player.getWorld().spawnParticle(Particle.HEART,
            player.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0);
    }

    // ── Utils ────────────────────────────────────────────────────────────────

    private FlowerEntry getNearestFlower(Player player, double maxDist) {
        List<FlowerEntry> list = flowerMap.get(player.getWorld().getUID());
        if (list == null || list.isEmpty()) return null;

        long now = System.currentTimeMillis();
        list.removeIf(f -> now > f.expireMs);

        FlowerEntry nearest = null;
        double minDist = maxDist * maxDist;
        for (FlowerEntry f : list) {
            double d = player.getLocation().distanceSquared(
                f.location.clone().add(0.5, 0, 0.5));
            if (d < minDist) { minDist = d; nearest = f; }
        }
        return nearest;
    }

    private void removeFlowerById(World world, int entityId) {
        // Xoa khoi map
        List<FlowerEntry> list = flowerMap.get(world.getUID());
        if (list != null) list.removeIf(f -> f.entityId == entityId);

        // Xoa entity
        for (Entity e : world.getEntities()) {
            if (e.getEntityId() == entityId) { e.remove(); break; }
        }
    }

    private LivingEntity getTargetInSight(Player player, double range) {
        return player.getTargetEntity((int) range) instanceof LivingEntity le
            && !(le instanceof Player) ? le : null;
    }

    private Location findGround(Location loc) {
        Location l = loc.clone();
        for (int i = 0; i < 5; i++) {
            if (!l.getBlock().getType().isAir()) { l.add(0, 1, 0); return l; }
            l.subtract(0, 1, 0);
        }
        return loc;
    }

    private String color(String s) { return s.replace("&", "\u00a7"); }

    // ── Inner ────────────────────────────────────────────────────────────────

    public static class FlowerEntry {
        public final int      entityId;
        public final Location location;
        public final long     expireMs;

        FlowerEntry(int entityId, Location location, long expireMs) {
            this.entityId = entityId;
            this.location = location;
            this.expireMs = expireMs;
        }
    }
}
