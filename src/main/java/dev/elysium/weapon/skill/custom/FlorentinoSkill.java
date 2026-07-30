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
 * Florentino Sword
 *
 * PASSIVE  : Moi 20s san sang. Chem trung → Dash den hoa + Sweep AOE.
 *            Lan 1,2: dame binh thuong. Lan 3: dame + hat tung.
 *            Nhat hoa → reset passive ngay.
 *
 * SKILL 1  : Click phai — Nem hoa 5 block, trung mob → 3 hoa + slow 2s. CD 7s, 12 mana.
 *
 * ULTIMATE : Shift+Click phai — Lao thang ra truoc, trung mob →
 *            3 hoa + danh dau muc tieu (Glowing + icon) 14s +
 *            buff ban than (khang choang/hat tung/cham/troi) 14s. CD 15s.
 */
public class FlorentinoSkill {

    private final ElysiumWeapon plugin;

    // ── Constants ─────────────────────────────────────────────────────────────
    public static final String PASSIVE_KEY  = "FLORENTINO_READY";
    public static final String CD_KEY       = "FLORENTINO_PASSIVE_CD";
    public static final int    PASSIVE_CD   = 20;

    public static final String SKILL1_CD    = "FLORENTINO_SKILL1_CD";
    public static final int    SKILL1_CD_S  = 7;
    public static final int    SKILL1_MANA  = 12;

    public static final String ULT_CD       = "FLORENTINO_ULT_CD";
    public static final int    ULT_CD_S     = 15;
    public static final int    ULT_MANA     = 30;

    // Dem so lan sweep de biet lan 3 hat tung
    private final Map<UUID, Integer> sweepCount = new HashMap<>();

    // Hoa tren san
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
                    if (!"FLORENTINO_SWORD".equals(state.getCurrentWeapon())) continue;
                    if (state.isOnCooldown(CD_KEY)) continue;
                    if (state.getPassiveStack(PASSIVE_KEY) > 0) continue;
                    state.addPassiveStack(PASSIVE_KEY, 1, 99999);
                    p.sendActionBar(color("&d✦ &fNoi tai san sang! &7[Chem trung → Dash]"));
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
                }
            }
        }.runTaskTimer(plugin, 400L, 400L);
    }

    // ── On Hit: Passive Dash ─────────────────────────────────────────────────

    public boolean onHit(Player player, LivingEntity target, PlayerWeaponState state) {
        if (state.getPassiveStack(PASSIVE_KEY) == 0) return false;

        FlowerEntry nearest = getNearestFlower(player, 20);
        if (nearest == null) return false;

        // Tieu passive + set CD
        state.clearPassiveStack(PASSIVE_KEY);
        state.setCooldown(CD_KEY, PASSIVE_CD);

        // Tang dem sweep
        int count = sweepCount.merge(player.getUniqueId(), 1, Integer::sum);
        boolean isThird = (count % 3 == 0);

        executeDash(player, nearest, state, isThird);
        return true;
    }

    // ── Dash + Sweep ─────────────────────────────────────────────────────────

    private void executeDash(Player player, FlowerEntry target,
                              PlayerWeaponState state, boolean hatTung) {
        Location from      = player.getLocation().clone();
        Location flowerLoc = target.location.clone().add(0.5, 0, 0.5);
        flowerLoc.setYaw(from.getYaw());
        flowerLoc.setPitch(from.getPitch());
        World world = player.getWorld();

        double baseDamage = getBaseDamage();
        double sweepDamage = baseDamage * 1.4;

        // Particle trail
        Vector trailDir = flowerLoc.toVector().subtract(from.toVector()).normalize();
        double trailDist = from.distance(flowerLoc);
        for (double d = 0; d < trailDist; d += 0.5) {
            Location p = from.clone().add(trailDir.clone().multiply(d)).add(0, 1, 0);
            world.spawnParticle(Particle.CHERRY_LEAVES, p, 3, 0.1, 0.1, 0.1, 0.01);
        }

        // Teleport
        player.teleport(flowerLoc);
        world.playSound(flowerLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.5f);
        world.spawnParticle(Particle.CHERRY_LEAVES, flowerLoc.clone().add(0,1,0),
                25, 0.6, 0.6, 0.6, 0.03);

        // Sweep AOE
        world.playSound(flowerLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        world.spawnParticle(Particle.SWEEP_ATTACK, flowerLoc.clone().add(0,1,0),
                5, 0.5, 0, 0.5, 0);

        for (Entity e : world.getNearbyEntities(flowerLoc, 2.5, 2, 2.5)) {
            if (!(e instanceof LivingEntity le) || e instanceof Player) continue;
            le.damage(sweepDamage, player);
            if (hatTung) {
                // Lan 3: hat tung
                Vector kb = le.getLocation().toVector()
                        .subtract(flowerLoc.toVector()).normalize().multiply(0.8);
                kb.setY(0.6);
                le.setVelocity(kb);
                world.spawnParticle(Particle.EXPLOSION, le.getLocation().add(0,1,0), 3);
            } else {
                Vector kb = le.getLocation().toVector()
                        .subtract(flowerLoc.toVector()).normalize().multiply(0.3);
                kb.setY(0.15);
                le.setVelocity(kb);
            }
            world.spawnParticle(Particle.CRIT, le.getLocation().add(0,1,0), 6, 0.3, 0.3, 0.3, 0.1);
        }

        String msg = hatTung
                ? "&d✦ &fSweep lan 3! &6Hat tung!"
                : "&d✦ &fDash + Sweep! &6x1.4 dame!";
        player.sendActionBar(color(msg));

        // Nhat hoa
        pickupFlower(player, target, state);
    }

    // ── Skill 1: Nem hoa ─────────────────────────────────────────────────────

    public void throwFlowers(Player player) {
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        if (state.isOnCooldown(SKILL1_CD)) {
            long rem = state.getCooldownRemaining(SKILL1_CD);
            player.sendActionBar(color("&cNem Hoa dang hoi! &e" + rem + "s"));
            return;
        }
        if (!dev.elysium.core.api.CoreAPI.useMana(player, SKILL1_MANA)) {
            player.sendActionBar(color("&cKhong du mana! Can: &b" + SKILL1_MANA));
            return;
        }
        state.setCooldown(SKILL1_CD, SKILL1_CD_S);

        World world = player.getWorld();
        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();

        new BukkitRunnable() {
            double dist = 0;
            @Override public void run() {
                dist += 0.5;
                Location cur = start.clone().add(dir.clone().multiply(dist));
                world.spawnParticle(Particle.CHERRY_LEAVES, cur, 3, 0.1, 0.1, 0.1, 0.01);

                LivingEntity hit = null;
                for (Entity e : world.getNearbyEntities(cur, 1, 1, 1)) {
                    if (e instanceof LivingEntity le && !(le instanceof Player)
                            && !e.equals(player)) {
                        hit = le; break;
                    }
                }

                if (hit != null || dist >= 5) {
                    cancel();
                    if (hit == null) return;
                    spawnFlowersAt(player, hit.getLocation());
                    hit.addPotionEffect(new PotionEffect(
                            PotionEffectType.SLOWNESS, 40, 1, false, true, true));
                    world.playSound(hit.getLocation(), Sound.BLOCK_CHERRY_LEAVES_PLACE, 1f, 1.2f);
                    player.sendActionBar(color("&d✦ &fTrung! 3 hoa xuat hien!"));
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ── Ultimate: Shift + Click phai ─────────────────────────────────────────

    public void castUltimate(Player player) {
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        if (state.isOnCooldown(ULT_CD)) {
            long rem = state.getCooldownRemaining(ULT_CD);
            player.sendActionBar(color("&5⚔ Ultimate dang hoi! &e" + rem + "s"));
            return;
        }
        if (!dev.elysium.core.api.CoreAPI.useMana(player, ULT_MANA)) {
            player.sendActionBar(color("&cKhong du mana! Can: &b" + ULT_MANA));
            return;
        }
        state.setCooldown(ULT_CD, ULT_CD_S);

        World world = player.getWorld();
        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();

        // Buff ban than: khang choang/hat tung/cham/troi 14s (280 tick)
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,   280, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 280, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,        280, 1, false, true, true));
        player.sendActionBar(color("&5⚔ &fUltimate! &dKhang tat ca hieu ung! 14s"));
        world.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.5f, 1.8f);
        world.spawnParticle(Particle.ENCHANT, player.getLocation().add(0,1,0),
                40, 0.5, 0.5, 0.5, 0.2);

        new BukkitRunnable() {
            double dist = 0;
            @Override public void run() {
                dist += 0.6;
                Location cur = start.clone().add(dir.clone().multiply(dist));
                world.spawnParticle(Particle.ENCHANTED_HIT, cur, 4, 0.1, 0.1, 0.1, 0.05);

                LivingEntity hit = null;
                for (Entity e : world.getNearbyEntities(cur, 1.2, 1.2, 1.2)) {
                    if (e instanceof LivingEntity le && !(le instanceof Player)
                            && !e.equals(player)) {
                        hit = le; break;
                    }
                }

                if (hit != null || dist >= 10) {
                    cancel();
                    if (hit == null) return;

                    // 3 hoa xung quanh
                    spawnFlowersAt(player, hit.getLocation());

                    // Danh dau muc tieu: Glowing 14s
                    hit.setGlowing(true);
                    hit.addPotionEffect(new PotionEffect(
                            PotionEffectType.GLOWING, 280, 0, false, true, true));
                    hit.setCustomName(color("&5⚔ &f" + getEntityName(hit)));
                    hit.setCustomNameVisible(true);

                    // Slow muc tieu
                    hit.addPotionEffect(new PotionEffect(
                            PotionEffectType.SLOWNESS, 280, 0, false, true, true));

                    world.playSound(hit.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
                    world.spawnParticle(Particle.CRIT_MAGIC, hit.getLocation().add(0,1,0),
                            20, 0.5, 0.5, 0.5, 0.1);
                    world.spawnParticle(Particle.ENCHANTED_HIT, hit.getLocation().add(0,1,0),
                            15, 0.4, 0.4, 0.4, 0.08);

                    // Tu bo danh dau sau 14s
                    final LivingEntity marked = hit;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        marked.setGlowing(false);
                        marked.setCustomNameVisible(false);
                        marked.setCustomName(null);
                    }, 280L);
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ── Spawn hoa chung ──────────────────────────────────────────────────────

    private void spawnFlowersAt(Player player, Location center) {
        World world = center.getWorld();
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(i * 120.0);
            Location flowerLoc = findGround(new Location(world,
                    center.getX() + 1.5 * Math.cos(angle),
                    center.getY(),
                    center.getZ() + 1.5 * Math.sin(angle)));

            Item flower = world.dropItem(flowerLoc.clone().add(0, 0.3, 0),
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
    }

    // ── Nhat hoa ────────────────────────────────────────────────────────────

    private void pickupFlower(Player player, FlowerEntry entry, PlayerWeaponState state) {
        removeFlowerById(player.getWorld(), entry.entityId);
        state.setCooldown(CD_KEY, 0);
        state.addPassiveStack(PASSIVE_KEY, 1, 99999);
        player.sendActionBar(color("&d✦ &fNhat hoa! Noi tai san sang!"));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.6f);
        player.getWorld().spawnParticle(Particle.HEART,
                player.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0);
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    public FlowerEntry getNearestFlower(Player player, double maxDist) {
        List<FlowerEntry> list = flowerMap.get(player.getWorld().getUID());
        if (list == null || list.isEmpty()) return null;
        long now = System.currentTimeMillis();
        list.removeIf(f -> now > f.expireMs);
        FlowerEntry nearest = null;
        double minDist = maxDist * maxDist;
        for (FlowerEntry f : list) {
            double d = player.getLocation().distanceSquared(f.location.clone().add(0.5, 0, 0.5));
            if (d < minDist) { minDist = d; nearest = f; }
        }
        return nearest;
    }

    private void removeFlowerById(World world, int entityId) {
        List<FlowerEntry> list = flowerMap.get(world.getUID());
        if (list != null) list.removeIf(f -> f.entityId == entityId);
        for (Entity e : world.getEntities()) {
            if (e.getEntityId() == entityId) { e.remove(); break; }
        }
    }

    private double getBaseDamage() {
        try {
            var wd = plugin.getWeaponManager().getWeaponData("FLORENTINO_SWORD");
            if (wd != null) return wd.getBaseDamage();
        } catch (Exception ignored) {}
        return 8.0;
    }

    private Location findGround(Location loc) {
        Location l = loc.clone();
        for (int i = 0; i < 5; i++) {
            if (!l.getBlock().getType().isAir()) { l.add(0, 1, 0); return l; }
            l.subtract(0, 1, 0);
        }
        return loc;
    }

    private String getEntityName(LivingEntity e) {
        return e.getCustomName() != null ? e.getCustomName() : e.getType().name();
    }

    private String color(String s) { return s.replace("&", "\u00a7"); }

    // ── Inner ─────────────────────────────────────────────────────────────────

    public static class FlowerEntry {
        public final int      entityId;
        public final Location location;
        public final long     expireMs;
        FlowerEntry(int entityId, Location location, long expireMs) {
            this.entityId = entityId; this.location = location; this.expireMs = expireMs;
        }
    }
}
