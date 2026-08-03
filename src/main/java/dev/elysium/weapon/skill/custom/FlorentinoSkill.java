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

    public static final String BUOC_HOA_BUFF   = "FLORENTINO_BUOC_HOA_BUFF";
    public static final String SKILL1_CD       = "FLORENTINO_SKILL1_CD";
    public static final int    SKILL1_CD_S     = 7;

    public static final String ULT_CD_KEY      = "FLORENTINO_ULT_CD";
    public static final int    ULT_CD_S        = 15;
    public static final int    ULT_DURATION    = 280; // 14s

    private final Map<UUID, Set<UUID>> markedTargets = new HashMap<>();
    private final Map<UUID, List<FlowerEntry>> flowerMap = new HashMap<>();
    
    // Combo Chiêu 2 + Timer tự reset
    private final Map<UUID, Integer> vortexComboMap = new HashMap<>();
    private final Map<UUID, Long> vortexLastHitMap = new HashMap<>();

    private boolean isInternalDamage = false;

    public FlorentinoSkill(ElysiumWeapon plugin) {
        this.plugin = plugin;
        startFlowerPickupTask();
        startComboResetTask();
    }

    public boolean isInternalDamage() {
        return isInternalDamage;
    }

    private boolean isValidTarget(Player caster, Entity entity) {
        if (!(entity instanceof LivingEntity target) || entity.equals(caster)) return false;
        if (target.isDead() || !target.isValid()) return false;
        if (target.hasMetadata("NPC")) return false;
        
        if (target instanceof Player p) {
            return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
        }
        return true;
    }

    private String getSkill1Id(Player player) {
        WeaponData wd = plugin.getWeaponManager().getHeldWeaponData(player);
        return (wd != null && wd.getSkill1() != null) ? wd.getSkill1().getId() : SKILL1_CD;
    }

    private String getUltId(Player player) {
        WeaponData wd = plugin.getWeaponManager().getHeldWeaponData(player);
        if (wd != null) {
            if (wd.getSkill2() != null) return wd.getSkill2().getId();
            if (wd.getUltimate() != null) return wd.getUltimate().getId();
        }
        return ULT_CD_KEY;
    }

    // ── HỆ THỐNG GÂY SÁT THƯƠNG TRỰC TIẾP CHUẨN ───────────────────────────────

    public void dealSkillDamage(LivingEntity target, Player damager, double physicalDmg, double percentHpTrueDmg) {
        isInternalDamage = true;
        try {
            if (isMarked(damager, target)) {
                physicalDmg *= 1.30;
                percentHpTrueDmg *= 1.30;
            }

            double masteryBonus = getMasteryDamageBonus(damager);
            physicalDmg *= masteryBonus;

            double targetMaxHp = target.getMaxHealth();
            double trueDamage = (targetMaxHp * (percentHpTrueDmg / 100.0));
            double totalDamage = physicalDmg + trueDamage;

            // Override hoàn toàn noDamageTicks để chống nuốt đòn combo
            target.setNoDamageTicks(0);
            target.damage(totalDamage, damager);

            if (percentHpTrueDmg > 0) {
                target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1.2, 0), (int) Math.max(1, percentHpTrueDmg), 0.2, 0.2, 0.2, 0.1);
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

    // ── TỐI ƯU HÓA QUÉT HOA TỰ ĐỘNG (PROXIMITY TICK) ──────────────────────────

    private void startFlowerPickupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (flowerMap.isEmpty()) return;

                for (World world : Bukkit.getWorlds()) {
                    List<FlowerEntry> flowers = flowerMap.get(world.getUID());
                    if (flowers == null || flowers.isEmpty()) continue;

                    long now = System.currentTimeMillis();
                    flowers.removeIf(f -> now > f.expireMs);

                    if (flowers.isEmpty()) continue;

                    for (Player player : world.getPlayers()) {
                        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
                        if (!"FLORENTINO_SWORD".equals(state.getCurrentWeapon())) continue;

                        Location pLoc = player.getLocation();
                        Iterator<FlowerEntry> iterator = flowers.iterator();

                        while (iterator.hasNext()) {
                            FlowerEntry flower = iterator.next();
                            if (flower.location.distanceSquared(pLoc) <= 5.5) { // ~2.3m
                                onPickupFlower(player, flower, state);
                                removeFlowerEntity(world, flower.entityId);
                                iterator.remove();
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private void onPickupFlower(Player player, FlowerEntry flower, PlayerWeaponState state) {
        state.addPassiveStack(BUOC_HOA_BUFF, 1, 100); // 5 giây sẵn sàng lướt

        double maxHp = player.getMaxHealth();
        double healAmount = maxHp * 0.08;
        player.setHealth(Math.min(maxHp, player.getHealth() + healAmount));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 1, false, false, false));

        String s1Id = getSkill1Id(player);
        if (state.isOnCooldown(s1Id) || state.isOnCooldown(SKILL1_CD)) {
            double remaining = state.getCooldownRemaining(s1Id);
            int newCd = (int) Math.max(0, Math.round(remaining - 1.5));
            state.setCooldown(s1Id, newCd);
            state.setCooldown(SKILL1_CD, newCd);
        }

        player.sendActionBar(color("&d✦ &fĐã nhặt hoa! &a+" + (int)healAmount + " HP &b[Sẵn sàng Lướt Kiếm]"));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.6f);
    }

    // ── ĐÒN LƯỚT ĐÁNH CƯỜNG HÓA (ĐÃ FIX KHÔNG BỊ TRÙNG HIT SÁT THƯƠNG) ────────

    public boolean executeDashAttack(Player player, PlayerWeaponState state) {
        if (state.getPassiveStack(BUOC_HOA_BUFF) <= 0) return false;

        state.clearPassiveStack(BUOC_HOA_BUFF);

        World world = player.getWorld();
        Location from = player.getLocation();
        Vector dir = from.getDirection().setY(0).normalize();

        Vector velocity = dir.multiply(1.35).setY(0.12);
        player.setVelocity(velocity);

        world.playSound(from, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.5f);
        world.spawnParticle(Particle.SWEEP_ATTACK, from.clone().add(dir.clone().multiply(1.5)).add(0, 1, 0), 1);
        world.spawnParticle(Particle.CHERRY_LEAVES, from.clone().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0.05);

        // Set theo dõi các mục tiêu đã bị đánh trúng trong lần lướt này
        Set<UUID> hitTargetsThisDash = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks++;
                for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), 2.5, 2.0, 2.5)) {
                    if (isValidTarget(player, e)) {
                        LivingEntity target = (LivingEntity) e;
                        if (!hitTargetsThisDash.contains(target.getUniqueId())) {
                            hitTargetsThisDash.add(target.getUniqueId());
                            dealSkillDamage(target, player, getBaseDamage() * 1.4, 4.0);
                        }
                    }
                }
                if (ticks >= 3) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return true;
    }

    // ── SKILL 1: NÉM HOA ─────────────────────────────────────────────────────

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
                dist += 0.8;
                Location cur = start.clone().add(dir.clone().multiply(dist));
                world.spawnParticle(Particle.CHERRY_LEAVES, cur, 2, 0.05, 0.05, 0.05, 0.01);

                LivingEntity hit = null;
                for (Entity e : world.getNearbyEntities(cur, 1.1, 1.1, 1.1)) {
                    if (isValidTarget(player, e)) {
                        hit = (LivingEntity) e;
                        break;
                    }
                }

                if (hit != null || dist >= 8) {
                    cancel();
                    if (hit == null) return;

                    hit.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, 255, false, false, false));
                    dealSkillDamage(hit, player, getBaseDamage(), 0.0);

                    spawnFlowersAt(hit.getLocation(), world, player);
                    world.playSound(hit.getLocation(), Sound.BLOCK_CHERRY_LEAVES_PLACE, 1.0f, 1.2f);
                    player.sendActionBar(color("&d✦ &fTrúng mục tiêu! Bắn ra 3 hoa!"));
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ── SKILL 2: THƯỞNG KIẾM (CÓ TỰ ĐỘNG RESET COMBO SAU 3S) ─────────────────

    private void startComboResetTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                vortexLastHitMap.entrySet().removeIf(entry -> {
                    if (now - entry.getValue() > 3000L) { // 3 giây không đánh -> Reset
                        vortexComboMap.remove(entry.getKey());
                        return true;
                    }
                    return false;
                });
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void triggerVortexCombo(Player player, LivingEntity target) {
        UUID playerUUID = player.getUniqueId();
        int count = vortexComboMap.getOrDefault(playerUUID, 0) + 1;

        World world = player.getWorld();
        Location center = target.getLocation().add(0, 1, 0);
        double baseDmg = getBaseDamage();

        vortexLastHitMap.put(playerUUID, System.currentTimeMillis());

        if (count == 1) {
            dealSkillDamage(target, player, baseDmg * 1.0, 2.0);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false, false));
            world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
            player.sendActionBar(color("&d✦ &bThưởng Kiếm I &7[Slow 30%]"));
            vortexComboMap.put(playerUUID, 1);
        } else if (count == 2) {
            dealSkillDamage(target, player, baseDmg * 1.2, 4.0);
            target.setVelocity(new Vector(0, 0.35, 0));
            world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8f, 1.4f);
            player.sendActionBar(color("&d✦ &eThưởng Kiếm II &c&l[HẤT TUNG!]"));
            vortexComboMap.put(playerUUID, 2);
        } else {
            dealSkillDamage(target, player, baseDmg * 1.8, 6.0);
            world.spawnParticle(Particle.CRIT, center, 15, 0.3, 0.3, 0.3, 0.1);
            world.playSound(center, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.5f);
            player.sendActionBar(color("&d✦ &c&lThưởng Kiếm III &4[Chí Mạng + True Dmg]"));
            vortexComboMap.put(playerUUID, 0);
        }
    }

    // ── ULTIMATE: TÀI HOA ─────────────────────────────────────────────────────

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
                dist += 0.9;
                Location cur = start.clone().add(dir.clone().multiply(dist));
                world.spawnParticle(Particle.CHERRY_LEAVES, cur, 3, 0.1, 0.1, 0.1, 0.01);

                LivingEntity target = null;
                for (Entity e : world.getNearbyEntities(cur, 1.3, 1.3, 1.3)) {
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

        world.playSound(start, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.8f);
        player.sendActionBar(color("&5✦ &d&lTài Hoa!"));
    }

    private void onUltimateHit(Player player, LivingEntity target, PlayerWeaponState state, World world) {
        Location hitLoc = target.getLocation();

        dealSkillDamage(target, player, getBaseDamage() * 2.2, 5.0);
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

        world.playSound(hitLoc, Sound.ENTITY_ENDER_DRAGON_HURT, 0.6f, 1.5f);
        player.sendActionBar(color("&5✦ &d&lTài Hoa! &7[Ghim Mục Tiêu +30% Dame] &a[Miễn Khống 14s]"));

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

    // ── RECYCLE & CLEANUP DỰ DỮ LIỆU KHI OUT / RELOAD ─────────────────────────

    public void clearPlayerData(UUID uuid) {
        markedTargets.remove(uuid);
        vortexComboMap.remove(uuid);
        vortexLastHitMap.remove(uuid);
    }

    public void cleanupAll() {
        for (World world : Bukkit.getWorlds()) {
            List<FlowerEntry> flowers = flowerMap.get(world.getUID());
            if (flowers != null) {
                for (FlowerEntry f : flowers) {
                    removeFlowerEntity(world, f.entityId);
                }
            }
        }
        flowerMap.clear();
        markedTargets.clear();
        vortexComboMap.clear();
        vortexLastHitMap.clear();
    }

    // ── HELPER UTILS ─────────────────────────────────────────────────────────

    private void spawnFlowersAt(Location center, World world, Player player) {
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(i * 120.0);
            double ox = 2.2 * Math.cos(angle);
            double oz = 2.2 * Math.sin(angle);
            Location flowerLoc = findGround(new Location(world, center.getX() + ox, center.getY(), center.getZ() + oz));

            Item flower = world.dropItem(flowerLoc.clone().add(0, 0.2, 0), new ItemStack(Material.POPPY));
            flower.setPickupDelay(Integer.MAX_VALUE);
            flower.setVelocity(new Vector(0, 0, 0));
            flower.setGlowing(true);
            flower.setInvulnerable(true);
            flower.setCustomName(color("&d✦ Hoa Florentino"));
            flower.setCustomNameVisible(true);

            long expireMs = System.currentTimeMillis() + 8_000L;
            flowerMap.computeIfAbsent(world.getUID(), k -> new ArrayList<>()).add(new FlowerEntry(flower.getEntityId(), flowerLoc, expireMs));

            final int eid = flower.getEntityId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> removeFlowerEntity(world, eid), 160L);
        }
    }

    public boolean isMarked(Player player, LivingEntity target) {
        Set<UUID> set = markedTargets.get(player.getUniqueId());
        return set != null && set.contains(target.getUniqueId());
    }

    private double getBaseDamage() {
        try {
            var wd = plugin.getWeaponManager().getWeaponData("FLORENTINO_SWORD");
            return (wd != null) ? wd.getBaseDamage() : 8.0;
        } catch (Exception ignored) { return 8.0; }
    }

    private void removeFlowerEntity(World world, int entityId) {
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
