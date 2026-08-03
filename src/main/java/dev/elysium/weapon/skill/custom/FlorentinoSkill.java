package dev.elysium.weapon.skill.custom;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class FlorentinoSkill {

    private final ElysiumWeapon plugin;

    public static final String SKILL1_CD     = "FLORENTINO_SKILL1_CD";
    public static final int    SKILL1_CD_S   = 7;

    public static final String BUOC_HOA_KEY  = "FLORENTINO_BUOC_HOA";
    public static final String VORTEX_KEY    = "FLORENTINO_VORTEX";

    public static final String ULT_CD_KEY    = "FLORENTINO_ULT_CD";
    public static final int    ULT_CD_S      = 15;

    private static final long DASH_COOLDOWN_MS = 100L;
    private final Map<UUID, Long> lastDashTimes = new HashMap<>();

    private final Map<UUID, Set<UUID>> markedTargets = new HashMap<>();
    private final Map<UUID, List<FlowerEntry>> flowerMap = new HashMap<>();
    private final Map<UUID, Integer> vortexHitCounters = new HashMap<>();

    private boolean isInternalDamage = false;

    public FlorentinoSkill(ElysiumWeapon plugin) {
        this.plugin = plugin;
    }

    public boolean isInternalDamage() { return isInternalDamage; }

    private boolean isValidTarget(Player caster, Entity entity) {
        if (!(entity instanceof LivingEntity target) || entity.equals(caster)) return false;
        if (target.isDead() || !target.isValid() || target.hasMetadata("NPC")) return false;
        if (target instanceof Player p) {
            return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
        }
        return true;
    }

    // ── Ném Hoa (Chiêu 1) ───────────────────────────────────────────────────

    public void throwFlowers(Player player) {
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        if (state.isOnCooldown(SKILL1_CD)) {
            player.sendActionBar(color("&cNém Hoa đang hồi: " + state.getCooldownRemaining(SKILL1_CD) + "s"));
            return;
        }

        state.setCooldown(SKILL1_CD, SKILL1_CD_S);
        World world = player.getWorld();
        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();

        new BukkitRunnable() {
            double dist = 0;
            @Override public void run() {
                dist += 0.8;
                Location cur = start.clone().add(dir.clone().multiply(dist));
                world.spawnParticle(Particle.CHERRY_LEAVES, cur, 2, 0.05, 0.05, 0.05, 0.01);

                LivingEntity hit = null;
                for (Entity e : world.getNearbyEntities(cur, 1.0, 1.0, 1.0)) {
                    if (isValidTarget(player, e)) { hit = (LivingEntity) e; break; }
                }

                if (hit != null || dist >= 8) {
                    cancel();
                    if (hit == null) return;
                    
                    hit.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, 255));
                    dealSkillDamage(hit, player, getBaseDamage());
                    spawnFlowersAt(hit.getLocation(), world, player);
                    player.sendActionBar(color("&d✦ Trúng Hoa!"));
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ── Teleport Lướt Nhặt Hoa (Đã fix mượt camera) ─────────────────────────

    public boolean handleHitAndDash(Player player, LivingEntity target, PlayerWeaponState state, EntityDamageByEntityEvent event) {
        long now = System.currentTimeMillis();
        if (now - lastDashTimes.getOrDefault(player.getUniqueId(), 0L) < DASH_COOLDOWN_MS) return false;

        FlowerEntry nearest = getNearestFlower(player, 5.0);
        if (nearest == null) return false;

        lastDashTimes.put(player.getUniqueId(), now);

        // 1. Tính Dame
        double dashDamage = getBaseDamage() * 1.35;
        if (isMarked(player, target)) dashDamage *= 1.30;
        event.setDamage(dashDamage);

        // 2. Fix Teleport: Giữ NGUYÊN Yaw & Pitch của Player để không giật camera
        Location targetTp = nearest.location.clone().add(0, 0.1, 0);
        targetTp.setYaw(player.getLocation().getYaw());
        targetTp.setPitch(player.getLocation().getPitch());
        
        player.teleport(targetTp);

        // 3. Xóa ticks bất tử ngay lập tức để không bị nuốt đòn đánh
        target.setNoDamageTicks(0);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.6f);
        player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, player.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.05);

        // 4. Nhặt hoa
        pickupFlower(player, target, nearest, state);
        return true;
    }

    private void pickupFlower(Player player, LivingEntity target, FlowerEntry entry, PlayerWeaponState state) {
        removeFlowerById(player.getWorld(), entry.entityId);

        state.addPassiveStack(BUOC_HOA_KEY, 1, 60);
        state.clearPassiveStack(VORTEX_KEY);
        state.addPassiveStack(VORTEX_KEY, 3, 200);

        double maxHp = player.getMaxHealth();
        double healAmount = maxHp * 0.08;
        if (target != null && isMarked(player, target)) healAmount *= 2.0;

        player.setHealth(Math.min(maxHp, player.getHealth() + healAmount));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, 1, false, false, false));

        if (state.isOnCooldown(SKILL1_CD)) {
            double remaining = state.getCooldownRemaining(SKILL1_CD);
            state.setCooldown(SKILL1_CD, (int) Math.max(0, remaining - 2));
        }

        player.sendActionBar(color("&d✦ Nhặt hoa! &a+" + (int)healAmount + " HP &b[-2s C1]"));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.8f);
    }

    // ── Chiêu 2 (Thưởng Kiếm) ──────────────────────────────────────────────────

    public boolean onHitDuringVortex(Player player, LivingEntity target, PlayerWeaponState state, EntityDamageByEntityEvent event) {
        int stacks = state.getPassiveStack(VORTEX_KEY);
        if (stacks <= 0) return false;

        state.clearPassiveStack(VORTEX_KEY);
        if (stacks - 1 > 0) state.addPassiveStack(VORTEX_KEY, stacks - 1, 200);

        UUID pUUID = player.getUniqueId();
        int count = vortexHitCounters.getOrDefault(pUUID, 0) + 1;
        double baseDmg = getBaseDamage();
        double finalDmg = baseDmg;

        if (count == 1) {
            finalDmg = baseDmg * 0.9;
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1));
            player.sendActionBar(color("&d✦ Thưởng Kiếm 1 &7[Làm chậm]"));
            vortexHitCounters.put(pUUID, 1);
        } else if (count == 2) {
            finalDmg = baseDmg * 1.1;
            target.setVelocity(new Vector(0, 0.25, 0));
            player.sendActionBar(color("&d✦ Thưởng Kiếm 2 &e[Hất tung]"));
            vortexHitCounters.put(pUUID, 2);
        } else {
            finalDmg = baseDmg * 1.7;
            player.sendActionBar(color("&d✦ Thưởng Kiếm 3 &c&l[Chí Mạng]"));
            vortexHitCounters.put(pUUID, 0);
        }

        if (isMarked(player, target)) finalDmg *= 1.30;
        event.setDamage(finalDmg);

        target.setNoDamageTicks(0);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
        return true;
    }

    // ── Chiêu 3 (Tài Hoa) ─────────────────────────────────────────────────────

    public void castUltimate(Player player) {
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        if (state.isOnCooldown(ULT_CD_KEY)) {
            player.sendActionBar(color("&cTài Hoa đang hồi: " + state.getCooldownRemaining(ULT_CD_KEY) + "s"));
            return;
        }

        state.setCooldown(ULT_CD_KEY, ULT_CD_S);
        World world = player.getWorld();
        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();

        new BukkitRunnable() {
            double dist = 0;
            @Override public void run() {
                dist += 0.9;
                Location cur = start.clone().add(dir.clone().multiply(dist));
                world.spawnParticle(Particle.CHERRY_LEAVES, cur, 3, 0.1, 0.1, 0.1, 0.01);

                LivingEntity target = null;
                for (Entity e : world.getNearbyEntities(cur, 1.2, 1.2, 1.2)) {
                    if (isValidTarget(player, e)) { target = (LivingEntity) e; break; }
                }

                if (target != null || dist >= 9) {
                    cancel();
                    if (target == null) return;
                    
                    dealSkillDamage(target, player, getBaseDamage() * 1.8);
                    spawnFlowersAt(target.getLocation(), world, player);
                    
                    markedTargets.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(target.getUniqueId());
                    player.sendActionBar(color("&5✦ Tài Hoa! Ghim mục tiêu (+30% Dame)"));
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ── Helper Utils ─────────────────────────────────────────────────────────

    private void dealSkillDamage(LivingEntity target, Player damager, double physicalDmg) {
        isInternalDamage = true;
        try {
            target.setNoDamageTicks(0);
            target.damage(physicalDmg, damager);
        } finally {
            isInternalDamage = false;
        }
    }

    private void spawnFlowersAt(Location center, World world, Player player) {
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(i * 120.0);
            Location flowerLoc = center.clone().add(1.8 * Math.cos(angle), 0, 1.8 * Math.sin(angle));

            Item flower = world.dropItem(flowerLoc, new ItemStack(Material.POPPY));
            flower.setPickupDelay(Integer.MAX_VALUE);
            flower.setVelocity(new Vector(0, 0, 0));
            flower.setGlowing(true);

            long expireMs = System.currentTimeMillis() + 8000L;
            flowerMap.computeIfAbsent(world.getUID(), k -> new ArrayList<>()).add(new FlowerEntry(flower.getEntityId(), flowerLoc, expireMs));

            final int eid = flower.getEntityId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> removeFlowerById(world, eid), 160L);
        }
    }

    public boolean isMarked(Player player, LivingEntity target) {
        Set<UUID> set = markedTargets.get(player.getUniqueId());
        return set != null && set.contains(target.getUniqueId());
    }

    private double getBaseDamage() { return 8.0; }

    private FlowerEntry getNearestFlower(Player player, double maxDist) {
        List<FlowerEntry> list = flowerMap.get(player.getWorld().getUID());
        if (list == null || list.isEmpty()) return null;
        
        long now = System.currentTimeMillis();
        list.removeIf(f -> now > f.expireMs);

        FlowerEntry nearest = null;
        double minDistSq = maxDist * maxDist;
        Location pLoc = player.getLocation();

        for (FlowerEntry f : list) {
            double distSq = pLoc.distanceSquared(f.location);
            if (distSq < minDistSq) {
                minDistSq = distSq;
                nearest = f;
            }
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

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    public static class FlowerEntry {
        public final int entityId;
        public final Location location;
        public final long expireMs;

        FlowerEntry(int entityId, Location location, long expireMs) {
            this.entityId = entityId;
            this.location = location;
            this.expireMs = expireMs;
        }
    }
}
