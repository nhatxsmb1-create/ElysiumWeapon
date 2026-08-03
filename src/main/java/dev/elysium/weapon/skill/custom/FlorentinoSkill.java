package dev.elysium.weapon.skill.custom;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import dev.elysium.weapon.weapon.WeaponData;
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

    // ── Keys & Config ────────────────────────────────────────────────────────
    public static final String PASSIVE_KEY   = "FLORENTINO_READY";
    public static final String CD_KEY        = "FLORENTINO_PASSIVE_CD";

    public static final String SKILL1_CD     = "FLORENTINO_SKILL1_CD";
    public static final int    SKILL1_CD_S   = 7;    // giây

    public static final String BUOC_HOA_KEY  = "FLORENTINO_BUOC_HOA";
    public static final String VORTEX_KEY    = "FLORENTINO_VORTEX";

    public static final String ULT_CD_KEY    = "FLORENTINO_ULT_CD";
    public static final int    ULT_CD_S      = 15;   // giây
    public static final int    ULT_DURATION  = 280;  // 14 giây

    // Cooldown lướt hoa (250ms)
    private static final long DASH_COOLDOWN_MS = 250L;
    private final Map<UUID, Long> lastDashTimes = new HashMap<>();

    private final Map<UUID, Set<UUID>> markedTargets = new HashMap<>();
    private final Map<UUID, List<FlowerEntry>> flowerMap = new HashMap<>();
    private final Map<UUID, Integer> vortexHitCounters = new HashMap<>();

    private boolean isInternalDamage = false;

    public FlorentinoSkill(ElysiumWeapon plugin) {
        this.plugin = plugin;
        startPassiveTimer();
    }

    public boolean isInternalDamage() {
        return isInternalDamage;
    }

    private boolean isValidTarget(Player caster, Entity entity) {
        if (!(entity instanceof LivingEntity target) || entity.equals(caster)) return false;
        if (target.isDead() || !target.isValid()) return false;
        if (target.hasMetadata("NPC")) return false; // Bỏ qua NPC
        
        if (target instanceof Player p) {
            return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
        }
        return true;
    }

    private String getSkill1Id(Player player) {
        WeaponData wd = plugin.getWeaponManager().getHeldWeaponData(player);
        if (wd != null && wd.getSkill1() != null) {
            return wd.getSkill1().getId();
        }
        return SKILL1_CD;
    }

    private String getUltId(Player player) {
        WeaponData wd = plugin.getWeaponManager().getHeldWeaponData(player);
        if (wd != null) {
            if (wd.getSkill2() != null) return wd.getSkill2().getId();
            if (wd.getUltimate() != null) return wd.getUltimate().getId();
        }
        return ULT_CD_KEY;
    }

    // ── CƠ CHẾ SÁT THƯƠNG MỚI: DIRECT HP OVERRIDE (CHỐNG NUỐT ĐÒN 100%) ─────────

    private void dealSkillDamage(LivingEntity target, Player damager, double physicalDmg, double percentHpTrueDmg) {
        isInternalDamage = true;
        try {
            // 1. Tính toán sát thương theo Mastery và Trạng thái Marked
            if (isMarked(damager, target)) {
                physicalDmg *= 1.30;
                percentHpTrueDmg *= 1.30;
            }

            double masteryBonus = getMasteryDamageBonus(damager);
            physicalDmg *= masteryBonus;

            double targetMaxHp = target.getMaxHealth();
            double trueDamage = (targetMaxHp * (percentHpTrueDmg / 100.0));
            double totalDamage = physicalDmg + trueDamage;

            // 2. Lấy HP hiện tại và trừ trực tiếp (Bỏ qua 100% bất tử NMS / Paper)
            double currentHp = target.getHealth();
            double finalHp = currentHp - totalDamage;

            // 3. Hiệu ứng giật đỏ người (Mô phỏng đòn đánh thật)
            target.playEffect(EntityEffect.HURT);

            // 4. Xử lý trừ máu & ghi nhận Kill / Aggro cho Server
            if (finalHp <= 0) {
                // Nếu đòn đánh kết liễu target -> Gọi dame lớn để Server ghi nhận Kill chính xác cho Damager
                target.setNoDamageTicks(0);
                target.damage(999999.0, damager);
            } else {
                // Trừ máu trực tiếp
                target.setHealth(Math.max(0.1, finalHp));

                // Gây 1 lượng dame giả lập cực nhỏ để server ghi nhận Combat Tag & Aggro Mob
                target.setNoDamageTicks(0);
                target.damage(0.0001, damager);
                target.setNoDamageTicks(0);
            }

            // 5. Hiệu ứng True Damage
            if (percentHpTrueDmg > 0) {
                target.getWorld().spawnParticle(
                    Particle.DAMAGE_INDICATOR, 
                    target.getLocation().add(0, 1.2, 0), 
                    (int) Math.max(1, percentHpTrueDmg), 
                    0.2, 0.2, 0.2, 0.1
                );
            }
        } finally {
            isInternalDamage = false;
        }
    }

    private double getMasteryDamageBonus(Player player) {
        try {
            return plugin.getWeaponMastery().getDamageBonus(player, "FLORENTINO_SWORD", "SKILL1");
        } catch (Exception e) {
            return 1.0;
        }
    }

    private void dealSkillDamage(LivingEntity target, Player damager, double physicalDmg) {
        dealSkillDamage(target, damager, physicalDmg, 0.0);
    }

    // ── Passive Timer ────────────────────────────────────────────────────────

    private void startPassiveTimer() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    PlayerWeaponState state = plugin.getWeaponManager().getState(p);
                    if (!"FLORENTINO_SWORD".equals(state.getCurrentWeapon())) continue;
                    if (state.isOnCooldown(CD_KEY)) continue;
                    if (state.getPassiveStack(PASSIVE_KEY) > 0) continue;

                    state.addPassiveStack(PASSIVE_KEY, 1, 99999);
                    p.sendActionBar(color("&d✦ &fNội tại sẵn sàng!"));
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
                }
            }
        }.runTaskTimer(plugin, 400L, 400L);
    }

    // ── Skill 1: Ném hoa ─────────────────────────────────────────────────────

    public void throwFlowers(Player player) {
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        String s1Id = getSkill1Id(player);

        if (state.isOnCooldown(s1Id) || state.isOnCooldown(SKILL1_CD)) {
            player.sendActionBar(color("&cNém Hoa đang hồi! &e" + state.getCooldownRemaining(s1Id) + "s"));
            return;
        }

        int cdModifier = plugin.getWeaponMastery().getCooldownModifier(player, "FLORENTINO_SWORD", "SKILL1");
        int finalCd = Math.max(1, SKILL1_CD_S + cdModifier);
        state.setCooldown(s1Id, finalCd);
        state.setCooldown(SKILL1_CD, finalCd);

        World world = player.getWorld();
        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();

        new BukkitRunnable() {
            double dist = 0;
            @Override public void run() {
                dist += 0.6;
                Location cur = start.clone().add(dir.clone().multiply(dist));
                world.spawnParticle(Particle.CHERRY_LEAVES, cur, 1, 0.05, 0.05, 0.05, 0.01);

                LivingEntity hit = null;
                for (Entity e : world.getNearbyEntities(cur, 1.0, 1.0, 1.0)) {
                    if (isValidTarget(player, e)) {
                        hit = (LivingEntity) e; 
                        break;
                    }
                }

                if (hit != null || dist >= 7) {
                    cancel();
                    if (hit == null) return;
                    
                    hit.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 255, false, false, false));
                    hit.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 10, 128, false, false, false));
                    dealSkillDamage(hit, player, getBaseDamage());
                    
                    spawnFlowersAt(hit.getLocation(), world, player);
                    world.playSound(hit.getLocation(), Sound.BLOCK_CHERRY_LEAVES_PLACE, 0.8f, 1.2f);
                    player.sendActionBar(color("&d✦ &fChoáng & Trúng 3 hoa!"));
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ── Lướt nhặt hoa ────────────────────────────────────────────────────────

    public boolean handleHitAndDash(Player player, LivingEntity target, PlayerWeaponState state) {
        long now = System.currentTimeMillis();
        long lastDash = lastDashTimes.getOrDefault(player.getUniqueId(), 0L);

        if (now - lastDash < DASH_COOLDOWN_MS) return false;

        FlowerEntry nearest = getNearestFlower(player, 14.0);
        if (nearest == null) return false;

        lastDashTimes.put(player.getUniqueId(), now);
        state.clearPassiveStack(PASSIVE_KEY);
        executeDash(player, target, nearest, state);
        return true;
    }

    private void executeDash(Player player, LivingEntity target, FlowerEntry flower, PlayerWeaponState state) {
        Location flowerLoc = flower.location.clone().add(0.5, 0, 0.5);
        Location from = player.getLocation();
        Location land = flowerLoc.clone();

        if (target != null && target.isValid()) {
            Vector dirToTarget = target.getLocation().add(0, 1, 0).subtract(land).toVector();
            if (dirToTarget.lengthSquared() > 0) land.setDirection(dirToTarget);
        } else {
            land.setYaw(from.getYaw());
            land.setPitch(from.getPitch());
        }

        player.teleport(land);
        player.getWorld().playSound(land, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.5f);
        player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, land.clone().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0.02);

        double baseDamage = getBaseDamage();
        final double dashDamage = baseDamage * 1.3;

        for (Entity e : player.getWorld().getNearbyEntities(from, 4, 2, 4)) {
            if (!isValidTarget(player, e)) continue;
            LivingEntity le = (LivingEntity) e;
            double t = dotProject(from, flowerLoc, le.getLocation());
            if (t >= 0 && t <= 1.2 && distToLine(from, flowerLoc, le.getLocation()) < 1.5) {
                dealSkillDamage(le, player, dashDamage, 3.0);
            }
        }

        pickupFlower(player, target, flower, state);
    }

    // ── Nhặt hoa ──────────────────────────────────────────────────────────────

    private void pickupFlower(Player player, LivingEntity target, FlowerEntry entry, PlayerWeaponState state) {
        removeFlowerById(player.getWorld(), entry.entityId);

        state.addPassiveStack(PASSIVE_KEY, 1, 99999);
        state.addPassiveStack(BUOC_HOA_KEY, 1, 60);
        state.clearPassiveStack(VORTEX_KEY);
        state.addPassiveStack(VORTEX_KEY, 3, 200);

        double maxHp = player.getMaxHealth();
        double healAmount = maxHp * 0.08;

        if (target != null && isMarked(player, target)) {
            healAmount *= 2.0;
        }

        player.setHealth(Math.min(maxHp, player.getHealth() + healAmount));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 25, 1, false, false, false));

        String s1Id = getSkill1Id(player);
        if (state.isOnCooldown(s1Id) || state.isOnCooldown(SKILL1_CD)) {
            double remaining = state.getCooldownRemaining(s1Id);
            int newCd = (int) Math.max(0, Math.round(remaining - 1.5));
            state.setCooldown(s1Id, newCd);
            state.setCooldown(SKILL1_CD, newCd);
        }

        player.sendActionBar(color("&d✦ &fNhặt hoa! &a+" + (int)healAmount + " HP &b[-1.5s C1]"));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.6f);
    }

    // ── Chiêu 2 (Thưởng Kiếm) ──────────────────────────────────────────────────

    public boolean onHitDuringVortex(Player player, LivingEntity target, PlayerWeaponState state) {
        int stacks = state.getPassiveStack(VORTEX_KEY);
        if (stacks <= 0) return false;

        state.clearPassiveStack(VORTEX_KEY);
        int remaining = stacks - 1;
        if (remaining > 0) state.addPassiveStack(VORTEX_KEY, remaining, 200);

        World world = player.getWorld();
        Location center = target.getLocation().clone().add(0, 1, 0);
        double baseDmg = getBaseDamage();

        UUID playerUUID = player.getUniqueId();
        int count = vortexHitCounters.getOrDefault(playerUUID, 0) + 1;

        if (count == 1) {
            dealSkillDamage(target, player, baseDmg * 0.8, 0.0);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false, false));
            world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.2f);
            player.sendActionBar(color("&d✦ &bThưởng Kiếm 1 &7[Slow]"));
            vortexHitCounters.put(playerUUID, 1);

        } else if (count == 2) {
            dealSkillDamage(target, player, baseDmg * 1.0, 3.0);
            target.setVelocity(new Vector(0, 0.35, 0));
            world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.7f, 1.4f);
            player.sendActionBar(color("&d✦ &eThưởng Kiếm 2 &c&l[HẤT TUNG!]"));
            vortexHitCounters.put(playerUUID, 2);

        } else {
            dealSkillDamage(target, player, baseDmg * 1.6, 5.0);

            world.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 15, 0.2, 0.4, 0.2, 0.1);
            world.playSound(center, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.6f);
            player.sendActionBar(color("&d✦ &c&lThưởng Kiếm 3 &4[Chí Mạng + True Dmg]"));
            vortexHitCounters.put(playerUUID, 0);
        }

        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians((360.0 / 8) * i);
            world.spawnParticle(Particle.CHERRY_LEAVES,
                center.clone().add(2.0 * Math.cos(angle), 0, 2.0 * Math.sin(angle)),
                1, 0.05, 0.05, 0.05, 0.01);
        }

        return true;
    }

    // ── Ultimate: Tài Hoa ─────────────────────────────────────────────────────

    public void castUltimate(Player player) {
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        String ultId = getUltId(player);

        if (state.isOnCooldown(ultId) || state.isOnCooldown(ULT_CD_KEY)) {
            player.sendActionBar(color("&cTài Hoa đang hồi! &e" + state.getCooldownRemaining(ultId) + "s"));
            return;
        }

        int ultCdModifier = plugin.getWeaponMastery().getCooldownModifier(player, "FLORENTINO_SWORD", "ULTIMATE");
        int finalUltCd = Math.max(1, ULT_CD_S + ultCdModifier);
        state.setCooldown(ultId, finalUltCd);
        state.setCooldown(ULT_CD_KEY, finalUltCd);

        World world = player.getWorld();
        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();

        new BukkitRunnable() {
            double dist = 0;
            boolean hit = false;

            @Override public void run() {
                if (hit || dist >= 9) { cancel(); return; }
                dist += 0.8;
                Location cur = start.clone().add(dir.clone().multiply(dist));

                world.spawnParticle(Particle.CHERRY_LEAVES, cur, 2, 0.1, 0.1, 0.1, 0.01);

                LivingEntity target = null;
                for (Entity e : world.getNearbyEntities(cur, 1.2, 1.2, 1.2)) {
                    if (isValidTarget(player, e)) {
                        target = (LivingEntity) e; 
                        break;
                    }
                }

                if (target != null) {
                    hit = true;
                    cancel();
                    onUltimateHit(player, target, state, world);
                }
            }
        }.runTaskTimer(plugin, 0, 1);

        world.playSound(start, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 2.0f);
        player.sendActionBar(color("&5✦ &d&lTài Hoa!"));
    }

    private void onUltimateHit(Player player, LivingEntity target, PlayerWeaponState state, World world) {
        Location hitLoc = target.getLocation();

        Location land = hitLoc.clone().add(-player.getLocation().getDirection().getX(), 0, -player.getLocation().getDirection().getZ());
        land.setYaw(player.getLocation().getYaw());
        land.setPitch(player.getLocation().getPitch());
        player.teleport(land);

        dealSkillDamage(target, player, getBaseDamage() * 2.0);
        spawnFlowersAt(hitLoc, world, player);

        target.setGlowing(true);

        ArmorStand marker = (ArmorStand) world.spawnEntity(hitLoc.clone().add(0, target.getHeight() + 0.4, 0), EntityType.ARMOR_STAND);
        marker.setCustomName(color("&c⚔ &4Marked (+30% Dame)"));
        marker.setCustomNameVisible(true);
        marker.setGravity(false);
        marker.setVisible(false);
        marker.setSmall(true);

        UUID playerUUID = player.getUniqueId();
        UUID targetUUID = target.getUniqueId();
        markedTargets.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(targetUUID);

        state.addPassiveStack("FLORENTINO_CC_IMMUNE", 1, ULT_DURATION);

        world.playSound(hitLoc, Sound.ENTITY_ENDER_DRAGON_HURT, 0.5f, 1.6f);
        player.sendActionBar(color("&5✦ &d&lTài Hoa! &7[Ghim Mục Tiêu +30% Dame] &a[Miễn CC 14s]"));

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += 2;
                if (ticks >= ULT_DURATION || !target.isValid() || target.isDead() || !player.isOnline()) {
                    if (marker.isValid()) marker.remove();
                    if (target.isValid()) target.setGlowing(false);

                    Set<UUID> set = markedTargets.get(playerUUID);
                    if (set != null) set.remove(targetUUID);
                    state.clearPassiveStack("FLORENTINO_CC_IMMUNE");
                    cancel();
                    return;
                }

                marker.teleport(target.getLocation().add(0, target.getHeight() + 0.4, 0));
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private void spawnFlowersAt(Location center, World world, Player player) {
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(i * 120.0);
            double ox = 2.0 * Math.cos(angle);
            double oz = 2.0 * Math.sin(angle);
            Location flowerLoc = findGround(new Location(world, center.getX() + ox, center.getY(), center.getZ() + oz));

            Item flower = world.dropItem(flowerLoc.clone().add(0, 0.2, 0), new ItemStack(Material.POPPY));
            flower.setPickupDelay(Integer.MAX_VALUE);
            flower.setVelocity(new Vector(0, 0, 0));
            flower.setGlowing(true);
            flower.setInvulnerable(true);
            flower.setCustomName(color("&d✦ Hoa Florentino"));
            flower.setCustomNameVisible(true);

            long expireMs = System.currentTimeMillis() + 10_000L;
            flowerMap.computeIfAbsent(world.getUID(), k -> new ArrayList<>()).add(new FlowerEntry(flower.getEntityId(), flowerLoc, expireMs));

            final int eid = flower.getEntityId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> removeFlowerById(world, eid), 200L);
        }
    }

    public boolean isMarked(Player player, LivingEntity target) {
        Set<UUID> set = markedTargets.get(player.getUniqueId());
        return set != null && set.contains(target.getUniqueId());
    }

    public boolean hasCCImmune(PlayerWeaponState state) {
        return state.getPassiveStack("FLORENTINO_CC_IMMUNE") > 0;
    }

    private double getBaseDamage() {
        try {
            var wd = plugin.getWeaponManager().getWeaponData("FLORENTINO_SWORD");
            return (wd != null) ? wd.getBaseDamage() : 8.0;
        } catch (Exception ignored) { return 8.0; }
    }

    private FlowerEntry getNearestFlower(Player player, double maxDist) {
        List<FlowerEntry> list = flowerMap.get(player.getWorld().getUID());
        if (list == null || list.isEmpty()) return null;
        long now = System.currentTimeMillis();
        list.removeIf(f -> now > f.expireMs);

        FlowerEntry nearest = null;
        double minDistSq = maxDist * maxDist;
        Location pLoc = player.getLocation();

        for (FlowerEntry f : list) {
            double dx = pLoc.getX() - (f.location.getX() + 0.5);
            double dy = (pLoc.getY() - f.location.getY()) * 0.5;
            double dz = pLoc.getZ() - (f.location.getZ() + 0.5);
            double distSq = dx * dx + dy * dy + dz * dz;

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

    private Location findGround(Location loc) {
        Location l = loc.clone();
        for (int i = 0; i < 5; i++) {
            if (!l.getBlock().getType().isAir()) { l.add(0, 1, 0); return l; }
            l.subtract(0, 1, 0);
        }
        return loc;
    }

    private String color(String s) { return s.replace("&", "\u00a7"); }

    private double dotProject(Location a, Location b, Location p) {
        double abx = b.getX()-a.getX(), abz = b.getZ()-a.getZ();
        double apx = p.getX()-a.getX(), apz = p.getZ()-a.getZ();
        double ab2 = abx*abx + abz*abz;
        if (ab2 == 0) return 0;
        return (apx*abx + apz*apz) / ab2;
    }

    private double distToLine(Location a, Location b, Location p) {
        double abx = b.getX()-a.getX(), abz = b.getZ()-a.getZ();
        double apx = p.getX()-a.getX(), apz = p.getZ()-a.getZ();
        double cross = abx*apz - abz*apx;
        double ab = Math.sqrt(abx*abx + abz*abz);
        return ab == 0 ? Math.sqrt(apx*apx+apz*apz) : Math.abs(cross)/ab;
    }

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
