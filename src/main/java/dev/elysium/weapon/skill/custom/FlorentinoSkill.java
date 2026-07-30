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

    // ── Keys ─────────────────────────────────────────────────────────────────
    public static final String PASSIVE_KEY   = "FLORENTINO_READY";
    public static final String CD_KEY        = "FLORENTINO_PASSIVE_CD";
    public static final int    PASSIVE_CD    = 20;   // giay

    public static final String SKILL1_CD     = "FLORENTINO_SKILL1_CD";
    public static final int    SKILL1_CD_S   = 7;    // giay
    public static final int    SKILL1_MANA   = 12;

    public static final String BUOC_HOA_KEY  = "FLORENTINO_BUOC_HOA";  // Noi tai: sau dash +30% dame
    public static final String VORTEX_KEY    = "FLORENTINO_VORTEX";    // Noi tai: 3 don chem lan AOE sau nhat hoa

    public static final String ULT_CD_KEY    = "FLORENTINO_ULT_CD";
    public static final int    ULT_CD_S      = 15;   // giay
    public static final int    ULT_MANA      = 30;
    public static final int    ULT_DURATION  = 280;  // tick = 14s (buff + mark + glow)

    // Player UUID → danh sach mob dang bi danh dau boi Ultimate
    private final Map<UUID, Set<UUID>> markedTargets = new HashMap<>();

    // World UID → danh sach hoa tren san
    private final Map<UUID, List<FlowerEntry>> flowerMap = new HashMap<>();

    // Bo dem so lan chem lan (Vortex) de gay hieu ung dung im 1s
    private final Map<UUID, Integer> vortexHitCounters = new HashMap<>();

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
                    p.sendActionBar(color("&d✦ &fNoi tai san sang! &7[Chem trung mob → TP den hoa]"));
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
                }
            }
        }.runTaskTimer(plugin, 400L, 400L);
    }

    // ── Skill 1: Nem hoa trung mob ───────────────────────────────────────────

    public void throwFlowers(Player player) {
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        if (state.isOnCooldown(SKILL1_CD)) {
            player.sendActionBar(color("&cNem Hoa dang hoi phuc! &e" + state.getCooldownRemaining(SKILL1_CD) + "s"));
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
                    if (e instanceof LivingEntity le && !(le instanceof Player) && !e.equals(player)) {
                        hit = le; break;
                    }
                }

                if (hit != null || dist >= 5) {
                    cancel();
                    if (hit == null) return;
                    spawnFlowersAt(hit.getLocation(), world, player);
                    hit.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true, true));
                    world.playSound(hit.getLocation(), Sound.BLOCK_CHERRY_LEAVES_PLACE, 1f, 1.2f);
                    world.spawnParticle(Particle.SWEEP_ATTACK, hit.getLocation().clone().add(0,1,0), 5);
                    player.sendActionBar(color("&d✦ &fTrung! 3 hoa xuat hien!"));
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ── Fix 1: Chem trung 1 lan la TP ngay lap tuc den hoa ────────────────────

    public boolean handleHitAndDash(Player player, LivingEntity target, PlayerWeaponState state) {
        FlowerEntry nearest = getNearestFlower(player, 20);
        if (nearest == null) return false;

        // Reset cooldown & xoa stack cu neu co de dash ngay
        state.clearPassiveStack(PASSIVE_KEY);
        state.setCooldown(CD_KEY, PASSIVE_CD);
        executeDash(player, nearest, state);
        return true;
    }

    // ── Dash den hoa ─────────────────────────────────────────────────────────

    private void executeDash(Player player, FlowerEntry target, PlayerWeaponState state) {
        Location flowerLoc = target.location.clone().add(0.5, 0, 0.5);
        Location from = player.getLocation();

        // Particle trail
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick++ >= 7) { cancel(); return; }
                player.getWorld().spawnParticle(Particle.CHERRY_LEAVES,
                    player.getLocation().add(0, 1, 0), 6, 0.2, 0.2, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 0, 1);

        Location land = flowerLoc.clone();
        land.setYaw(from.getYaw());
        land.setPitch(from.getPitch());
        player.teleport(land);

        player.getWorld().playSound(land, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);
        player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, land.clone().add(0,1,0),
            20, 0.5, 0.5, 0.5, 0.03);

        double baseDamage = getBaseDamage();
        final double dashDamage = baseDamage * 1.5;

        for (Entity e : player.getWorld().getNearbyEntities(from, 6, 2, 6)) {
            if (!(e instanceof LivingEntity le) || e instanceof Player) continue;
            double t = dotProject(from, flowerLoc, le.getLocation());
            if (t >= 0 && t <= 1.2 && distToLine(from, flowerLoc, le.getLocation()) < 2.0) {
                le.damage(dashDamage, player);
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0, false, false, false));
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, le.getLocation().add(0,1,0), 3);
            }
        }

        player.sendActionBar(color("&d✦ &fDash!"));
        pickupFlower(player, target, state);
    }

    // ── Nhat hoa → kich hoat Vortex ─────────────────────────────────────────

    private void pickupFlower(Player player, FlowerEntry entry, PlayerWeaponState state) {
        removeFlowerById(player.getWorld(), entry.entityId);

        state.setCooldown(CD_KEY, 0);
        state.addPassiveStack(PASSIVE_KEY, 1, 99999);

        // Buoc Hoa: don danh tiep trong 3s +30% dame
        state.addPassiveStack(BUOC_HOA_KEY, 1, 60);

        // Vortex: 3 don chem tiep theo lan AOE
        state.clearPassiveStack(VORTEX_KEY);
        state.addPassiveStack(VORTEX_KEY, 3, 200); // het han 10s

        player.sendActionBar(color("&d✦ &fNhat hoa! &6[Buoc Hoa +30%] &b[Vortex x3]"));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.6f);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0);
    }

    // ── Fix 2: Noi tai Vortex chem lan XOAN PHONG (3 lan gay DUNG IM 1s) ─────

    public boolean onHitDuringVortex(Player player, LivingEntity target, PlayerWeaponState state) {
        int stacks = state.getPassiveStack(VORTEX_KEY);
        if (stacks <= 0) return false;

        state.clearPassiveStack(VORTEX_KEY);
        int remaining = stacks - 1;
        if (remaining > 0) state.addPassiveStack(VORTEX_KEY, remaining, 200);

        World world     = player.getWorld();
        Location center = target.getLocation().clone().add(0, 1, 0);
        double aoeDamage = getBaseDamage() * 0.6;

        // Dem so lan chem lan Vortex
        UUID playerUUID = player.getUniqueId();
        int count = vortexHitCounters.getOrDefault(playerUUID, 0) + 1;

        // Chem lan kieu Xoan Phong cho tat ca mob xung quanh
        for (Entity e : world.getNearbyEntities(center, 3.5, 2, 3.5)) {
            if (!(e instanceof LivingEntity le) || e instanceof Player) continue;
            le.damage(aoeDamage, player);
            world.spawnParticle(Particle.SWEEP_ATTACK, le.getLocation().add(0, 1, 0), 3);
        }

        // Neu da chem lan du 3 lan -> Gay DUNG IM (Khóa di chuyển) 1 giay (20 ticks)
        if (count >= 3) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 255, false, false, false));
            target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20, 128, false, false, false));
            
            world.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
            world.playSound(center, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.5f);
            player.sendActionBar(color("&d✦ &c&lĐỨNG IM! &fMuc tieu bi khóa di chuyen 1s"));
            vortexHitCounters.put(playerUUID, 0); // Reset dem
        } else {
            vortexHitCounters.put(playerUUID, count);
            world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.3f);
            player.sendActionBar(color("&d✦ &bXoan Phong! &7[Lần " + count + "/3]"));
        }

        // Hieu ung hinh tron Xoan Phong
        for (int i = 0; i < 16; i++) {
            double angle = Math.toRadians((360.0 / 16) * i);
            world.spawnParticle(Particle.CHERRY_LEAVES,
                center.clone().add(2.5 * Math.cos(angle), 0, 2.5 * Math.sin(angle)),
                2, 0.1, 0.1, 0.1, 0.01);
        }

        return true;
    }

    // ── Ultimate: Shift + Click phai ─────────────────────────────────────────

    public void castUltimate(Player player) {
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        if (state.isOnCooldown(ULT_CD_KEY)) {
            player.sendActionBar(color("&cChieu cuoi dang hoi phuc! &e"
                + state.getCooldownRemaining(ULT_CD_KEY) + "s"));
            return;
        }
        if (!dev.elysium.core.api.CoreAPI.useMana(player, ULT_MANA)) {
            player.sendActionBar(color("&cKhong du mana! Can: &b" + ULT_MANA));
            return;
        }

        state.setCooldown(ULT_CD_KEY, ULT_CD_S);

        World world = player.getWorld();
        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();

        new BukkitRunnable() {
            double dist = 0;
            boolean hit = false;

            @Override public void run() {
                if (hit || dist >= 10) { cancel(); return; }
                dist += 0.6;
                Location cur = start.clone().add(dir.clone().multiply(dist));

                world.spawnParticle(Particle.SWEEP_ATTACK, cur, 2, 0.15, 0.15, 0.15, 0);
                world.spawnParticle(Particle.CHERRY_LEAVES, cur, 4, 0.2, 0.2, 0.2, 0.02);

                LivingEntity target = null;
                for (Entity e : world.getNearbyEntities(cur, 1.2, 1.2, 1.2)) {
                    if (e instanceof LivingEntity le && !e.equals(player) && !(le instanceof Player)) {
                        target = le; break;
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
        world.spawnParticle(Particle.SWEEP_ATTACK, start, 8, 0.3, 0.3, 0.3, 0.05);
        player.sendActionBar(color("&5✦ &d&lChieu Cuoi! &fLao toi!"));
    }

    // ── Fix 3: Danh dau tren dau muc tieu GHIM THEO LIÊN TỤC ─────────────────

    private void onUltimateHit(Player player, LivingEntity target, PlayerWeaponState state, World world) {
        Location hitLoc = target.getLocation();

        Location land = hitLoc.clone().add(
            -player.getLocation().getDirection().getX(), 0, -player.getLocation().getDirection().getZ());
        land.setYaw(player.getLocation().getYaw());
        land.setPitch(player.getLocation().getPitch());
        player.teleport(land);

        double ultDamage = getBaseDamage() * 2.0;
        target.damage(ultDamage, player);

        spawnFlowersAt(hitLoc, world, player);

        target.setGlowing(true);

        // Tạo ArmorStand đánh dấu
        ArmorStand marker = (ArmorStand) world.spawnEntity(
            hitLoc.clone().add(0, target.getHeight() + 0.4, 0), EntityType.ARMOR_STAND);
        marker.setCustomName(color("&c⚔ &4Marked"));
        marker.setCustomNameVisible(true);
        marker.setGravity(false);
        marker.setVisible(false);
        marker.setSmall(true);

        UUID playerUUID = player.getUniqueId();
        UUID targetUUID = target.getUniqueId();
        markedTargets.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(targetUUID);

        state.addPassiveStack("FLORENTINO_CC_IMMUNE", 1, ULT_DURATION);

        world.spawnParticle(Particle.SWEEP_ATTACK, hitLoc.clone().add(0,1,0), 12, 0.5, 0.5, 0.5, 0.05);
        world.spawnParticle(Particle.CHERRY_LEAVES, hitLoc.clone().add(0,1,0), 20, 0.6, 0.6, 0.6, 0.03);
        world.playSound(hitLoc, Sound.ENTITY_ENDER_DRAGON_HURT, 0.7f, 1.6f);
        world.playSound(hitLoc, Sound.BLOCK_CHERRY_LEAVES_PLACE, 1f, 0.8f);

        player.sendActionBar(color("&5✦ &d&lTrung! &73 hoa roi ra! &c[Marked] &a[Mien CC 14s]"));

        // TASK LẶP LẠI: Teleport ArmorStand liên tục theo đầu target khi target di chuyển
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks >= ULT_DURATION || !target.isValid() || target.isDead() || !player.isOnline()) {
                    if (marker.isValid()) marker.remove();
                    if (target.isValid()) target.setGlowing(false);

                    Set<UUID> set = markedTargets.get(playerUUID);
                    if (set != null) set.remove(targetUUID);
                    state.clearPassiveStack("FLORENTINO_CC_IMMUNE");

                    if (player.isOnline() && ticks >= ULT_DURATION) {
                        player.sendActionBar(color("&7[Hieu luc chieu cuoi ket thuc]"));
                        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.8f);
                    }
                    cancel();
                    return;
                }

                // Cập nhật vị trí ghim chặt trên đầu target mỗi tick
                marker.teleport(target.getLocation().add(0, target.getHeight() + 0.4, 0));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Helper: Spawn 3 hoa xung quanh ───────────────────────────────────────

    private void spawnFlowersAt(Location center, World world, Player player) {
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(i * 120.0);
            double ox = 1.5 * Math.cos(angle);
            double oz = 1.5 * Math.sin(angle);
            Location flowerLoc = findGround(
                new Location(world, center.getX() + ox, center.getY(), center.getZ() + oz));

            Item flower = world.dropItem(
                flowerLoc.clone().add(0, 0.3, 0), new ItemStack(Material.POPPY));
            flower.setPickupDelay(Integer.MAX_VALUE);
            flower.setVelocity(new Vector(0, 0, 0));
            flower.setGlowing(true);
            flower.setCustomName(color("&d✦ Hoa Florentino"));
            flower.setCustomNameVisible(true);

            world.spawnParticle(Particle.CHERRY_LEAVES, flower.getLocation(), 12, 0.3, 0.3, 0.3, 0.02);

            long expireMs = System.currentTimeMillis() + 10_000L;
            flowerMap.computeIfAbsent(world.getUID(), k -> new ArrayList<>())
                     .add(new FlowerEntry(flower.getEntityId(), flowerLoc, expireMs));

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
        return (apx*abx + apz*abz) / ab2;
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
        
