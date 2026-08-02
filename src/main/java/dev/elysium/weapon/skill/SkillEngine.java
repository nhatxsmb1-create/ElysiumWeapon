package dev.elysium.weapon.skill;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.animation.AnimationEngine;
import dev.elysium.weapon.weapon.WeaponData;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class SkillEngine {

    private final ElysiumWeapon plugin;
    private final AnimationEngine animationEngine;

    public SkillEngine(ElysiumWeapon plugin, AnimationEngine animationEngine) {
        this.plugin          = plugin;
        this.animationEngine = animationEngine;
    }

    // ── Execute Skill ─────────────────────────────────────────────────────────

    public void executeSkill(Player player, WeaponData weapon,
                              WeaponData.SkillData skill, PlayerWeaponState state) {
        // 1. Kiểm tra Cooldown trước
        if (state.isOnCooldown(skill.getId())) {
            long rem = state.getCooldownRemaining(skill.getId());
            player.sendMessage(color("&cKỹ năng đang hồi: &e" + rem + "s"));
            return;
        }

        // 2. Kiểm tra & Trừ Mana (Chỉ trừ nếu manaCost > 0)
        int manaCost = getManaCost(weapon, skill);
        if (manaCost > 0) {
            try {
                var ep = dev.elysium.core.api.CoreAPI.getPlayer(player);
                if (ep == null || ep.getMana() < manaCost) {
                    player.sendMessage(color("&cKhông đủ Mana! (" + (ep != null ? ep.getMana() : 0) + "/" + manaCost + ")"));
                    return;
                }
                ep.useMana(manaCost);
            } catch (Exception e) {
                plugin.getLogger().warning("Không thể truy cập CoreAPI Mana cho " + player.getName());
            }
        }

        // 3. Đặt Cooldown chuẩn với Mastery Reduction
        String skillSlot  = getSkillSlot(weapon, skill);
        int baseCooldown  = skill.getCooldown();
        int cdModifier    = plugin.getWeaponMastery().getCooldownModifier(player, weapon.getId(), skillSlot);
        int finalCooldown = Math.max(1, baseCooldown + cdModifier);
        state.setCooldown(skill.getId(), finalCooldown);

        // 4. Phân luồng xử lý Kỹ năng
        switch (skill.getId()) {
            case "BLADE_DASH"     -> executeBladeDash(player, weapon, skill, state);
            case "SPIN_SLASH"     -> executeSpinSlash(player, weapon, skill, state);
            case "SAKURA_BLOOM"   -> executeSakuraBloom(player, weapon, skill, state);
            case "GROUND_SLAM"    -> executeGroundSlam(player, weapon, skill, state);
            case "WAR_CRY"        -> executeWarCry(player, weapon, skill, state);
            case "TITAN_FALL"     -> executeTitanFall(player, weapon, skill, state);
            case "ICE_LANCE"      -> executeIceLance(player, weapon, skill, state);
            case "FROST_NOVA"     -> executeFrostNova(player, weapon, skill, state);
            case "ABSOLUTE_ZERO"  -> executeAbsoluteZero(player, weapon, skill, state);
            case "PIERCING_SHOT"  -> executePiercingShot(player, weapon, skill, state);
            case "RAIN_OF_ARROWS" -> executeRainOfArrows(player, weapon, skill, state);
            case "ELVEN_STORM"    -> executeElvenStorm(player, weapon, skill, state);
            case "SHADOW_STEP"    -> executeShadowStep(player, weapon, skill, state);
            case "BLADE_STORM"    -> executeBladeStorm(player, weapon, skill, state);
            case "ISSEN"          -> executeIssen(player, weapon, skill, state);
            default -> {
                // Ủy quyền cho Florentino Engine nếu là vũ khí cơ chế đặc biệt
                if (plugin.getFlorentinoEngine() != null && plugin.getFlorentinoEngine().isFlorentinoSkill(skill.getId())) {
                    plugin.getFlorentinoEngine().execute(player, skill);
                } else {
                    player.sendMessage(color("&cKỹ năng chưa được khai báo: " + skill.getId()));
                }
            }
        }

        // Hiệu ứng & Âm thanh
        animationEngine.play(player, skill.getAnimationType(), skill.getParticleType());
        playSound(player, skill.getSoundType());

        // Thông báo ActionBar
        player.sendActionBar(color("&b" + skill.getName() + " &f| &7CD: &e" + finalCooldown + "s"));
    }

    // ── WARRIOR Skills ────────────────────────────────────────────────────────

    private void executeBladeDash(Player player, WeaponData w, WeaponData.SkillData s, PlayerWeaponState state) {
        double dist    = s.getDouble("dash-distance", 6.0);
        double dmgMult = s.getDouble("damage-multiplier", 1.6);
        Vector dir     = player.getLocation().getDirection().normalize();

        player.setVelocity(dir.multiply(dist * 0.4));

        new BukkitRunnable() {
            @Override public void run() {
                for (Entity e : player.getNearbyEntities(2, 2, 2)) {
                    if (!isValidTarget(player, e)) continue;
                    dealDamageWithMastery(player, (LivingEntity) e, w.getBaseDamage() * dmgMult, w, "SKILL1");
                }
                spawnParticles(player.getLocation(), skill(s), 20, 0.5);
            }
        }.runTaskLater(plugin, 4L);
    }

    private void executeSpinSlash(Player player, WeaponData w, WeaponData.SkillData s, PlayerWeaponState state) {
        double radius  = s.getDouble("radius", 4.0);
        double dmgMult = s.getDouble("damage-multiplier", 1.3);

        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (!isValidTarget(player, e)) continue;
            dealDamageWithMastery(player, (LivingEntity) e, w.getBaseDamage() * dmgMult, w, "SKILL2");
        }
        spawnRingParticles(player.getLocation(), skill(s), radius);
    }

    private void executeSakuraBloom(Player player, WeaponData w, WeaponData.SkillData s, PlayerWeaponState state) {
        int    count   = s.getInt("projectile-count", 5);
        double dmgMult = s.getDouble("damage-multiplier", 2.0);

        for (int i = 0; i < count; i++) {
            final double angle = (360.0 / count) * i;
            new BukkitRunnable() {
                @Override public void run() {
                    double rad = Math.toRadians(angle);
                    Vector dir = new Vector(Math.cos(rad), 0.1, Math.sin(rad)).normalize();

                    Arrow arrow = player.getWorld().spawnArrow(player.getEyeLocation(), dir, 1.5f, 5f);
                    arrow.setShooter(player);
                    arrow.setDamage(w.getBaseDamage() * dmgMult);
                    arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);

                    spawnParticles(arrow.getLocation(), skill(s), 5, 0.1);
                }
            }.runTaskLater(plugin, i * 2L);
        }
    }

    private void executeGroundSlam(Player player, WeaponData w, WeaponData.SkillData s, PlayerWeaponState state) {
        double radius  = s.getDouble("radius", 5.0);
        double dmgMult = s.getDouble("damage-multiplier", 2.4);
        double kb      = s.getDouble("knockback", 4.0);
        Location loc   = player.getLocation();

        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (!isValidTarget(player, e)) continue;
            LivingEntity target = (LivingEntity) e;
            dealDamageWithMastery(player, target, w.getBaseDamage() * dmgMult, w, "SKILL1");
            
            Vector kbVec = target.getLocation().subtract(loc).toVector().normalize().multiply(kb * 0.3);
            kbVec.setY(0.4);
            target.setVelocity(kbVec);
        }
        spawnRingParticles(loc, skill(s), radius);
        loc.getWorld().createExplosion(loc, 0f, false, false);
    }

    private void executeWarCry(Player player, WeaponData w, WeaponData.SkillData s, PlayerWeaponState state) {
        double buffPct   = s.getDouble("damage-buff", 0.40);
        int    buffTicks = s.getInt("buff-duration", 120);
        double fearRad   = s.getDouble("fear-radius", 8.0);
        int    fearTicks = s.getInt("fear-duration", 40);

        state.addPassiveStack("WAR_CRY_BUFF", 1, buffTicks);

        for (Entity e : player.getNearbyEntities(fearRad, fearRad, fearRad)) {
            if (!isValidTarget(player, e)) continue;
            LivingEntity target = (LivingEntity) e;
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, fearTicks, 3));
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, fearTicks, 0));
        }

        player.sendActionBar(color("&6War Cry! &f+" + (int)(buffPct * 100) + "% Dame trong " + (buffTicks / 20) + "s"));
    }

    private void executeTitanFall(Player player, WeaponData w, WeaponData.SkillData s, PlayerWeaponState state) {
        double radius  = s.getDouble("radius", 8.0);
        double dmgMult = s.getDouble("damage-multiplier", 5.0);

        player.setVelocity(new Vector(0, 2.2, 0));

        new BukkitRunnable() {
            @Override public void run() {
                player.setVelocity(player.getLocation().getDirection().setY(-3.5));

                new BukkitRunnable() {
                    @Override public void run() {
                        Location impact = player.getLocation();
                        for (Entity e : impact.getWorld().getNearbyEntities(impact, radius, radius, radius)) {
                            if (!isValidTarget(player, e)) continue;
                            LivingEntity target = (LivingEntity) e;
                            dealDamageWithMastery(player, target, w.getBaseDamage() * dmgMult, w, "ULTIMATE");
                            target.setVelocity(new Vector(
                                    (target.getLocation().getX() - impact.getX()) * 0.3,
                                    1.3,
                                    (target.getLocation().getZ() - impact.getZ()) * 0.3
                            ));
                        }
                        spawnRingParticles(impact, skill(s), radius);
                        impact.getWorld().createExplosion(impact, 0f, false, false);
                    }
                }.runTaskLater(plugin, 12L);
            }
        }.runTaskLater(plugin, 20L);
    }

    // ── MAGE Skills ───────────────────────────────────────────────────────────

    private void executeIceLance(Player player, WeaponData w, WeaponData.SkillData s, PlayerWeaponState state) {
        double dmgMult = s.getDouble("damage-multiplier", 2.0);
        boolean pierce = s.getBoolean("pierce", true);

        new BukkitRunnable() {
            final Location loc = player.getEyeLocation();
            final Vector   dir = loc.getDirection().normalize();
            int ticks = 0;

            @Override public void run() {
                if (ticks++ > 30 || !player.isOnline()) { cancel(); return; }
                loc.add(dir.clone().multiply(1.5));
                spawnParticles(loc, skill(s), 3, 0.1);

                for (Entity e : loc.getWorld().getNearbyEntities(loc, 1.2, 1.2, 1.2)) {
                    if (!isValidTarget(player, e)) continue;
                    LivingEntity target = (LivingEntity) e;
                    dealDamageWithMastery(player, target, w.getBaseDamage() * dmgMult, w, "SKILL1");
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                    if (!pierce) { cancel(); return; }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void executeFrostNova(Player player, WeaponData w, WeaponData.SkillData s, PlayerWeaponState state) {
        double radius      = s.getDouble("radius", 3.5);
        int    freezeTicks = s.getInt("freeze-duration", 40);

        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (!isValidTarget(player, e)) continue;
            LivingEntity target = (LivingEntity) e;
            dealDamageWithMastery(player, target, w.getBaseDamage(), w, "SKILL2");
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeTicks, 10));
        }
        spawnRingParticles(player.getLocation(), skill(s), radius);
    }

    private void executeAbsoluteZero(Player player, WeaponData w, WeaponData.SkillData s, PlayerWeaponState state) {
        double radius       = s.getDouble("radius", 12.0);
        int    duration     = s.getInt("duration", 100);
        double finalDmgMult = s.getDouble("final-damage-multiplier", 4.0);
        int    slowAmp      = s.getInt("slow-amplifier", 3);
        Location center     = player.getLocation().clone();

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick++ >= duration) {
                    for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                        if (!isValidTarget(player, e)) continue;
                        dealDamageWithMastery(player, (LivingEntity) e, w.getBaseDamage() * finalDmgMult, w, "ULTIMATE");
                    }
                    spawnRingParticles(center, skill(s), radius);
                    cancel(); return;
                }
                if (tick % 5 == 0) {
                    for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                        if (!isValidTarget(player, e)) continue;
                        ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, slowAmp));
                    }
                    spawnParticles(center, skill(s), 15, radius * 0.4);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── RANGER Skills ─────────────────────────────────────────────────────────

    private void executePiercingShot(Player player, WeaponData w, WeaponData.SkillData s, PlayerWeaponState state) {
        double dmgMult = s.getDouble("damage-multiplier", 1.5);
        Arrow arrow    = player.launchProjectile(Arrow.class);
        arrow.setDamage(w.getBaseDamage() * dmgMult);
        arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
        arrow.setPierceLevel(10);
        spawnParticles(arrow.getLocation(), skill(s), 5, 0.2);
    }

    private void executeRainOfArrows(Player player, WeaponData w, WeaponData.SkillData s, PlayerWeaponState state) {
        int    count   = s.getInt("arrow-count", 12);
        double radius  = s.getDouble("radius", 6.0);
        double dmgMult = s.getDouble("damage-multiplier", 0.7);
        int    duration= s.getInt("duration", 60);

        Location targetLoc = player.getTargetBlockExact(30) != null
                ? player.getTargetBlockExact(30).getLocation().add(0.5, 6, 0.5)
                : player.getLocation().add(player.getLocation().getDirection().multiply(12)).add(0, 6, 0);

        for (int i = 0; i < count; i++) {
            final int delay = (int) ((duration / (double) count) * i);
            new BukkitRunnable() {
                @Override public void run() {
                    double offX = (Math.random() - 0.5) * radius * 2;
                    double offZ = (Math.random() - 0.5) * radius * 2;
                    Location from = targetLoc.clone().add(offX, 0, offZ);
                    Vector   dir  = new Vector(0, -1, 0);

                    Arrow arrow = player.getWorld().spawnArrow(from, dir, 2.5f, 0f);
                    arrow.setShooter(player);
                    arrow.setDamage(w.getBaseDamage() * dmgMult);
                    arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                }
            }.runTaskLater(plugin, delay);
        }
    }

    private void executeElvenStorm(Player player, WeaponData w, WeaponData.SkillData s, PlayerWeaponState state) {
        int    chargeTicks = s.getInt("charge-time", 60);
        double dmgMult     = s.getDouble("damage-multiplier", 6.0);

        state.startCharge();
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, chargeTicks, 5));

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!state.isCharging() || tick++ >= chargeTicks) {
                    state.stopCharge();
                    Arrow arrow = player.launchProjectile(Arrow.class);
                    arrow.setDamage(w.getBaseDamage() * dmgMult);
                    arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                    arrow.setVelocity(player.getLocation().getDirection().multiply(4.5));
                    cancel(); return;
                }
                double progress = (double) tick / chargeTicks;
                player.sendActionBar(color("&a▶ Đang tụ lực: &f" + String.format("%.0f%%", progress * 100)));
                spawnParticles(player.getLocation(), skill(s), 5, progress * 1.5);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── ASSASSIN Skills ───────────────────────────────────────────────────────

    private void executeShadowStep(Player player, WeaponData w, WeaponData.SkillData s, PlayerWeaponState state) {
        double dmgMult = s.getDouble("damage-multiplier", 2.0);
        LivingEntity target = getClosestTarget(player, 15);
        if (target == null) { player.sendMessage(color("&cKhông có mục tiêu trong phạm vi!")); return; }

        Location behind = target.getLocation().add(target.getLocation().getDirection().normalize().multiply(-1.5));
        behind.setY(target.getLocation().getY());
        player.teleport(behind);

        dealDamageWithMastery(player, target, w.getBaseDamage() * dmgMult * 1.5, w, "SKILL1");
        spawnParticles(target.getLocation(), skill(s), 20, 0.5);
    }

    private void executeBladeStorm(Player player, WeaponData w, WeaponData.SkillData s, PlayerWeaponState state) {
        int    hits    = s.getInt("hit-count", 5);
        double dmgMult = s.getDouble("damage-multiplier", 0.9);
        int    duration= s.getInt("duration", 40);

        new BukkitRunnable() {
            int count = 0;
            @Override public void run() {
                if (count++ >= hits) { cancel(); return; }
                for (Entity e : player.getNearbyEntities(3.0, 3.0, 3.0)) {
                    if (!isValidTarget(player, e)) continue;
                    dealDamageWithMastery(player, (LivingEntity) e, w.getBaseDamage() * dmgMult, w, "SKILL2");
                }
                spawnParticles(player.getLocation(), skill(s), 10, 1.0);
            }
        }.runTaskTimer(plugin, 0L, Math.max(1, duration / hits));
    }

    private void executeIssen(Player player, WeaponData w, WeaponData.SkillData s, PlayerWeaponState state) {
        int invDuration = s.getInt("invisibility-duration", 20);
        int speedBoost  = s.getInt("speed-boost", 2);
        int window      = s.getInt("damage-window", 60);

        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, invDuration, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, invDuration + window, speedBoost));

        state.addPassiveStack("ISSEN_READY", 1, window);
        player.sendActionBar(color("&5&lIssen Khai Nòng! &fMột nhát – Một mạng!"));
    }

    // ── Combo Execution ───────────────────────────────────────────────────────

    public void executeCombo(Player player, WeaponData weapon, WeaponData.ComboData combo, PlayerWeaponState state) {
        List<Double> damages = combo.getDamages();
        LivingEntity target  = getClosestTarget(player, 5);
        if (target == null) return;

        for (int i = 0; i < damages.size(); i++) {
            final int    idx    = i;
            final double dmgMult= damages.get(i);
            new BukkitRunnable() {
                @Override public void run() {
                    if (!target.isValid() || target.isDead()) return;

                    boolean ignoreArmor = false;
                    if (combo.getId().equals("IAIDO") && idx == 1) {
                        ignoreArmor = combo.getBoolean("second-hit-ignore-armor", true);
                    }

                    double masteryBonus = plugin.getWeaponMastery().getDamageBonus(player, weapon.getId(), "SKILL1");
                    double finalDmg     = weapon.getBaseDamage() * dmgMult * masteryBonus;

                    if (ignoreArmor) dealDamageIgnoreArmor(player, target, finalDmg);
                    else             dealDamage(player, target, finalDmg);

                    spawnParticles(target.getLocation().add(0, 1, 0), combo.getParticleType(), 8, 0.3);
                    playSound(player, combo.getSoundType());
                }
            }.runTaskLater(plugin, i * 4L);
        }

        if (combo.getId().equals("HEAVY_COMBO")) {
            double stunChance = combo.getDouble("stun-chance", 0.35);
            if (Math.random() < stunChance) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 10));
            }
        }

        state.resetCombo();
        animationEngine.play(player, combo.getAnimationType(), combo.getParticleType());
        player.sendActionBar(color("&e&l⚡ COMBO: " + combo.getName()));
        state.addWeaponExp(weapon.getId(), 10L);
    }

    // ── Damage Utils ──────────────────────────────────────────────────────────

    public void dealDamage(Player attacker, LivingEntity target, double damage) {
        if (!isValidTarget(attacker, target)) return;
        target.damage(damage, attacker);
    }

    public void dealDamageWithMastery(Player attacker, LivingEntity target, double damage, WeaponData weapon, String skillSlot) {
        if (!isValidTarget(attacker, target)) return;
        double bonus = plugin.getWeaponMastery().getDamageBonus(attacker, weapon.getId(), skillSlot);
        target.damage(damage * bonus, attacker);
    }

    public void dealDamageIgnoreArmor(Player attacker, LivingEntity target, double damage) {
        if (!isValidTarget(attacker, target)) return;
        double currentHealth = target.getHealth();
        target.damage(0.01, attacker);
        target.setHealth(Math.max(0.0, currentHealth - damage));
    }

    // ── Safe Helpers ──────────────────────────────────────────────────────────

    private boolean isValidTarget(Player attacker, Entity entity) {
        if (!(entity instanceof LivingEntity target) || entity == attacker) return false;
        if (target.isDead() || !target.isValid()) return false;
        if (target instanceof Player p) {
            return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
        }
        return true;
    }

    private LivingEntity getClosestTarget(Player player, double range) {
        LivingEntity closest = null;
        double minDistSq = range * range;
        for (Entity e : player.getNearbyEntities(range, range, range)) {
            if (!isValidTarget(player, e)) continue;
            double dSq = player.getLocation().distanceSquared(e.getLocation());
            if (dSq < minDistSq) {
                minDistSq = dSq;
                closest = (LivingEntity) e;
            }
        }
        return closest;
    }

    private String getSkillSlot(WeaponData w, WeaponData.SkillData s) {
        if (s == w.getSkill1())   return "SKILL1";
        if (s == w.getSkill2())   return "SKILL2";
        if (s == w.getUltimate()) return "ULTIMATE";
        return "SKILL1";
    }

    private int getManaCost(WeaponData w, WeaponData.SkillData s) {
        if (s == w.getSkill1())   return w.getManaCostSkill1();
        if (s == w.getSkill2())   return w.getManaCostSkill2();
        if (s == w.getUltimate()) return w.getManaCostUltimate();
        return 0;
    }

    private void spawnParticles(Location loc, String type, int count, double spread) {
        if (loc == null || loc.getWorld() == null) return;
        Particle p;
        try { p = Particle.valueOf(type); } catch (Exception e) { p = Particle.CRIT; }
        loc.getWorld().spawnParticle(p, loc, count, spread, spread, spread, 0.05);
    }

    private void spawnRingParticles(Location center, String type, double radius) {
        if (center == null || center.getWorld() == null) return;
        Particle p;
        try { p = Particle.valueOf(type); } catch (Exception e) { p = Particle.CRIT; }
        
        World world = center.getWorld();
        for (int i = 0; i < 36; i += 2) {
            double angle = Math.toRadians(i * 10);
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            world.spawnParticle(p, x, center.getY() + 0.2, z, 1, 0, 0, 0, 0);
        }
    }

    private void playSound(Player player, String soundStr) {
        if (soundStr == null) return;
        try {
            Sound sound = Sound.valueOf(soundStr);
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (Exception ignored) {}
    }

    private String skill(WeaponData.SkillData s) { return s != null ? s.getParticleType() : "CRIT"; }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
}
