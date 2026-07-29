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
        // Kiem tra mana
        int manaCost = getManaCost(weapon, skill);
        try {
            var ep = dev.elysium.core.api.CoreAPI.getPlayer(player);
            if (ep.getMana() < manaCost) {
                player.sendMessage(color("&cKhong du Mana! (" + ep.getMana() + "/" + manaCost + ")"));
                return;
            }
            ep.removeMana(manaCost);
        } catch (Exception ignored) {}

        // Kiem tra cooldown
        if (state.isOnCooldown(skill.getId())) {
            long rem = state.getCooldownRemaining(skill.getId());
            player.sendMessage(color("&cCooldown: &e" + rem + "s"));
            return;
        }

        // Set cooldown
        state.setCooldown(skill.getId(), skill.getCooldown());

        // Thuc thi skill theo ID
        switch (skill.getId()) {
            case "BLADE_DASH"    -> executeBladeDash(player, weapon, skill, state);
            case "SPIN_SLASH"    -> executeSpinSlash(player, weapon, skill, state);
            case "SAKURA_BLOOM"  -> executeSakuraBloom(player, weapon, skill, state);
            case "GROUND_SLAM"   -> executeGroundSlam(player, weapon, skill, state);
            case "WAR_CRY"       -> executeWarCry(player, weapon, skill, state);
            case "TITAN_FALL"    -> executeTitanFall(player, weapon, skill, state);
            case "ICE_LANCE"     -> executeIceLance(player, weapon, skill, state);
            case "FROST_NOVA"    -> executeFrostNova(player, weapon, skill, state);
            case "ABSOLUTE_ZERO" -> executeAbsoluteZero(player, weapon, skill, state);
            case "PIERCING_SHOT" -> executePiercingShot(player, weapon, skill, state);
            case "RAIN_OF_ARROWS"-> executeRainOfArrows(player, weapon, skill, state);
            case "ELVEN_STORM"   -> executeElvenStorm(player, weapon, skill, state);
            case "SHADOW_STEP"   -> executeShadowStep(player, weapon, skill, state);
            case "BLADE_STORM"   -> executeBladeStorm(player, weapon, skill, state);
            case "ISSEN"         -> executeIssen(player, weapon, skill, state);
            default -> player.sendMessage(color("&cSkill chua duoc implement: " + skill.getId()));
        }

        // Animation + Sound
        animationEngine.play(player, skill.getAnimationType(), skill.getParticleType());
        playSound(player, skill.getSoundType());

        // Actionbar thong bao
        player.sendActionBar(color("&b" + skill.getName() + " &f| &7CD: &e" + skill.getCooldown() + "s"));
    }

    // ── WARRIOR Skills ────────────────────────────────────────────────────────

    private void executeBladeDash(Player player, WeaponData w,
                                   WeaponData.SkillData s, PlayerWeaponState state) {
        double dist     = s.getDouble("dash-distance", 6.0);
        double dmgMult  = s.getDouble("damage-multiplier", 1.5);
        Vector dir      = player.getLocation().getDirection().normalize();

        // Dash
        player.setVelocity(dir.multiply(dist * 0.4));

        // Damage sau 0.3s
        new BukkitRunnable() {
            @Override public void run() {
                for (Entity e : player.getNearbyEntities(2, 2, 2)) {
                    if (!(e instanceof LivingEntity target) || e == player) continue;
                    dealDamage(player, target, w.getBaseDamage() * dmgMult);
                }
                spawnParticles(player.getLocation(), skill(s), 20, 0.5);
            }
        }.runTaskLater(plugin, 6L);
    }

    private void executeSpinSlash(Player player, WeaponData w,
                                   WeaponData.SkillData s, PlayerWeaponState state) {
        double radius  = s.getDouble("radius", 4.0);
        double dmgMult = s.getDouble("damage-multiplier", 1.2);

        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity target) || e == player) continue;
            dealDamage(player, target, w.getBaseDamage() * dmgMult);
        }
        spawnRingParticles(player.getLocation(), skill(s), radius);
    }

    private void executeSakuraBloom(Player player, WeaponData w,
                                     WeaponData.SkillData s, PlayerWeaponState state) {
        int    count   = s.getInt("projectile-count", 5);
        double dmgMult = s.getDouble("damage-multiplier", 2.0);

        for (int i = 0; i < count; i++) {
            final int idx   = i;
            final double angle = (360.0 / count) * i;
            new BukkitRunnable() {
                @Override public void run() {
                    double rad = Math.toRadians(angle);
                    Vector dir = new Vector(Math.cos(rad), 0.1, Math.sin(rad)).normalize();

                    Arrow arrow = player.getWorld().spawnArrow(
                            player.getEyeLocation(), dir, 1.5f, 5f);
                    arrow.setShooter(player);
                    arrow.setDamage(w.getBaseDamage() * dmgMult);
                    arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);

                    spawnParticles(arrow.getLocation(), skill(s), 5, 0.1);
                }
            }.runTaskLater(plugin, i * 3L);
        }
    }

    // ── WARRIOR Greatsword Skills ─────────────────────────────────────────────

    private void executeGroundSlam(Player player, WeaponData w,
                                    WeaponData.SkillData s, PlayerWeaponState state) {
        double radius  = s.getDouble("radius", 5.0);
        double dmgMult = s.getDouble("damage-multiplier", 2.0);
        double kb      = s.getDouble("knockback", 4.0);
        Location loc   = player.getLocation();

        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity target) || e == player) continue;
            dealDamage(player, target, w.getBaseDamage() * dmgMult);
            Vector kbVec = target.getLocation().subtract(loc).toVector()
                    .normalize().multiply(kb * 0.3);
            kbVec.setY(0.5);
            target.setVelocity(kbVec);
        }
        spawnRingParticles(loc, skill(s), radius);
        loc.getWorld().createExplosion(loc, 0f, false, false);
    }

    private void executeWarCry(Player player, WeaponData w,
                                WeaponData.SkillData s, PlayerWeaponState state) {
        double buffPct   = s.getDouble("damage-buff", 0.3);
        int    buffTicks = s.getInt("buff-duration", 120);
        double fearRad   = s.getDouble("fear-radius", 8.0);
        int    fearTicks = s.getInt("fear-duration", 40);

        // Buff damage vao state (check trong combat listener)
        state.addPassiveStack("WAR_CRY_BUFF", 1, buffTicks);

        // Fear: slow + blindness ke thu
        for (Entity e : player.getNearbyEntities(fearRad, fearRad, fearRad)) {
            if (!(e instanceof LivingEntity target) || e == player) continue;
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, fearTicks, 3));
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, fearTicks, 0));
        }

        player.sendActionBar(color("&6War Cry! &f+30% Dame trong " + (buffTicks / 20) + "s"));
    }

    private void executeTitanFall(Player player, WeaponData w,
                                   WeaponData.SkillData s, PlayerWeaponState state) {
        double radius  = s.getDouble("radius", 8.0);
        double dmgMult = s.getDouble("damage-multiplier", 4.0);

        // Phong len cao
        player.setVelocity(new Vector(0, 2.5, 0));

        // Sau 1.5s roi xuong va gay AOE
        new BukkitRunnable() {
            @Override public void run() {
                player.setVelocity(player.getLocation().getDirection().setY(-3));

                new BukkitRunnable() {
                    @Override public void run() {
                        Location impact = player.getLocation();
                        for (Entity e : impact.getWorld().getNearbyEntities(impact, radius, radius, radius)) {
                            if (!(e instanceof LivingEntity target) || e == player) continue;
                            dealDamage(player, target, w.getBaseDamage() * dmgMult);
                            // Knockup
                            target.setVelocity(new Vector(
                                    (target.getLocation().getX() - impact.getX()) * 0.3,
                                    1.5,
                                    (target.getLocation().getZ() - impact.getZ()) * 0.3
                            ));
                        }
                        spawnRingParticles(impact, skill(s), radius);
                        impact.getWorld().createExplosion(impact, 0f, false, false);
                    }
                }.runTaskLater(plugin, 25L);
            }
        }.runTaskLater(plugin, 30L);
    }

    // ── MAGE Skills ───────────────────────────────────────────────────────────

    private void executeIceLance(Player player, WeaponData w,
                                  WeaponData.SkillData s, PlayerWeaponState state) {
        double dmgMult = s.getDouble("damage-multiplier", 1.6);
        boolean pierce = s.getBoolean("pierce", true);

        // Phong projectile xuyen ke thu
        new BukkitRunnable() {
            final Location loc = player.getEyeLocation();
            final Vector   dir = loc.getDirection().normalize();
            int ticks = 0;

            @Override public void run() {
                if (ticks++ > 40) { cancel(); return; }
                loc.add(dir.clone().multiply(1.5));
                spawnParticles(loc, skill(s), 3, 0.1);

                for (Entity e : loc.getWorld().getNearbyEntities(loc, 1, 1, 1)) {
                    if (!(e instanceof LivingEntity target) || e == player) continue;
                    dealDamage(player, target, w.getBaseDamage() * dmgMult);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                    if (!pierce) { cancel(); return; }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void executeFrostNova(Player player, WeaponData w,
                                   WeaponData.SkillData s, PlayerWeaponState state) {
        double radius    = s.getDouble("radius", 3.0);
        int    freezeTicks = s.getInt("freeze-duration", 40);

        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity target) || e == player) continue;
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeTicks, 10));
            target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, freezeTicks, -1)); // visual freeze
        }
        spawnRingParticles(player.getLocation(), skill(s), radius);
    }

    private void executeAbsoluteZero(Player player, WeaponData w,
                                      WeaponData.SkillData s, PlayerWeaponState state) {
        double radius      = s.getDouble("radius", 12.0);
        int    duration    = s.getInt("duration", 100);
        double finalDmgMult= s.getDouble("final-damage-multiplier", 3.0);
        int    slowAmp     = s.getInt("slow-amplifier", 3);
        Location center    = player.getLocation().clone();

        // Tick zone
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick++ >= duration) {
                    // Final explosion
                    for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                        if (!(e instanceof LivingEntity target) || e == player) continue;
                        dealDamage(player, target, w.getBaseDamage() * finalDmgMult);
                        target.removePotionEffect(PotionEffectType.SLOWNESS);
                    }
                    spawnRingParticles(center, skill(s), radius);
                    cancel(); return;
                }
                // Ap dung slow moi 5 tick
                if (tick % 5 == 0) {
                    for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                        if (!(e instanceof LivingEntity target) || e == player) continue;
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, slowAmp));
                    }
                    // Visual
                    spawnParticles(center, skill(s), 20, radius * 0.5);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── RANGER Skills ─────────────────────────────────────────────────────────

    private void executePiercingShot(Player player, WeaponData w,
                                      WeaponData.SkillData s, PlayerWeaponState state) {
        double dmgMult = s.getDouble("damage-multiplier", 1.3);
        Arrow arrow    = player.launchProjectile(Arrow.class);
        arrow.setDamage(w.getBaseDamage() * dmgMult);
        arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
        arrow.setPierceLevel(10); // Paper API: xuyen nhieu entity
        spawnParticles(arrow.getLocation(), skill(s), 5, 0.2);
    }

    private void executeRainOfArrows(Player player, WeaponData w,
                                      WeaponData.SkillData s, PlayerWeaponState state) {
        int    count   = s.getInt("arrow-count", 12);
        double radius  = s.getDouble("radius", 6.0);
        double dmgMult = s.getDouble("damage-multiplier", 0.6);
        int    duration= s.getInt("duration", 60);
        Location target= player.getTargetBlockExact(30) != null
                ? player.getTargetBlockExact(30).getLocation().add(0.5, 5, 0.5)
                : player.getLocation().add(player.getLocation().getDirection().multiply(15)).add(0, 5, 0);

        for (int i = 0; i < count; i++) {
            final int delay = (int) ((duration / (double) count) * i);
            new BukkitRunnable() {
                @Override public void run() {
                    double offX = (Math.random() - 0.5) * radius * 2;
                    double offZ = (Math.random() - 0.5) * radius * 2;
                    Location from = target.clone().add(offX, 0, offZ);
                    Vector   dir  = from.clone().add(0, -5, 0).subtract(from).toVector().normalize();
                    dir.setY(-1);

                    Arrow arrow = player.getWorld().spawnArrow(from, dir, 2f, 0f);
                    arrow.setShooter(player);
                    arrow.setDamage(w.getBaseDamage() * dmgMult);
                    arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                }
            }.runTaskLater(plugin, delay);
        }
    }

    private void executeElvenStorm(Player player, WeaponData w,
                                    WeaponData.SkillData s, PlayerWeaponState state) {
        int    chargeTicks = s.getInt("charge-time", 60);
        double dmgMult     = s.getDouble("damage-multiplier", 5.0);
        double expRadius   = s.getDouble("explosion-radius", 3.0);

        state.startCharge();
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, chargeTicks, 5));

        // Visual charge
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!state.isCharging() || tick++ >= chargeTicks) {
                    state.stopCharge();
                    // Phong mui ten cuc manh
                    Arrow arrow = player.launchProjectile(Arrow.class);
                    arrow.setDamage(w.getBaseDamage() * dmgMult);
                    arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                    arrow.setVelocity(player.getLocation().getDirection().multiply(5));
                    cancel(); return;
                }
                double progress = (double) tick / chargeTicks;
                player.sendActionBar(color("&a▶ Sap nen: &f" + String.format("%.0f%%", progress * 100)));
                spawnParticles(player.getLocation(), skill(s), 5, (float)(progress * 2));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── ASSASSIN Skills ───────────────────────────────────────────────────────

    private void executeShadowStep(Player player, WeaponData w,
                                    WeaponData.SkillData s, PlayerWeaponState state) {
        double dmgMult = s.getDouble("damage-multiplier", 1.8);
        LivingEntity target = getClosestTarget(player, 15);
        if (target == null) { player.sendMessage(color("&cKhong co muc tieu!")); return; }

        // Teleport phia sau target
        Location behind = target.getLocation().add(
                target.getLocation().getDirection().normalize().multiply(-1.5));
        behind.setY(target.getLocation().getY());
        player.teleport(behind);

        // Backstab
        dealDamage(player, target, w.getBaseDamage() * dmgMult * 1.5); // backstab bonus
        spawnParticles(target.getLocation(), skill(s), 20, 0.5);
    }

    private void executeBladeStorm(Player player, WeaponData w,
                                    WeaponData.SkillData s, PlayerWeaponState state) {
        int    hits    = s.getInt("hit-count", 5);
        double dmgMult = s.getDouble("damage-multiplier", 0.8);
        int    duration= s.getInt("duration", 40);

        new BukkitRunnable() {
            int count = 0;
            @Override public void run() {
                if (count++ >= hits) { cancel(); return; }
                for (Entity e : player.getNearbyEntities(2.5, 2.5, 2.5)) {
                    if (!(e instanceof LivingEntity target) || e == player) continue;
                    dealDamage(player, target, w.getBaseDamage() * dmgMult);
                }
                spawnParticles(player.getLocation(), skill(s), 10, 1.0);
            }
        }.runTaskTimer(plugin, 0L, duration / hits);
    }

    private void executeIssen(Player player, WeaponData w,
                               WeaponData.SkillData s, PlayerWeaponState state) {
        int invDuration = s.getInt("invisibility-duration", 20);
        int speedBoost  = s.getInt("speed-boost", 2);
        int window      = s.getInt("damage-window", 60);

        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, invDuration, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, invDuration + window, speedBoost));

        // Danh dau state: lan danh ke tiep trong window se gay dame dac biet
        state.addPassiveStack("ISSEN_READY", 1, window);
        player.sendActionBar(color("&5&lIssen khai dong! &fMot nhat – Mot mang!"));
    }

    // ── Combo Execution ───────────────────────────────────────────────────────

    public void executeCombo(Player player, WeaponData weapon,
                              WeaponData.ComboData combo, PlayerWeaponState state) {
        List<Double> damages = combo.getDamages();
        LivingEntity target  = getClosestTarget(player, 5);
        if (target == null) return;

        for (int i = 0; i < damages.size(); i++) {
            final int    idx    = i;
            final double dmgMult= damages.get(i);
            new BukkitRunnable() {
                @Override public void run() {
                    if (!target.isValid()) return;

                    // Check special per-combo
                    boolean ignoreArmor = false;
                    if (combo.getId().equals("IAIDO") && idx == 1)
                        ignoreArmor = combo.getBoolean("second-hit-ignore-armor", false);

                    double finalDmg = weapon.getBaseDamage() * dmgMult;
                    if (ignoreArmor) dealDamageIgnoreArmor(player, target, finalDmg);
                    else             dealDamage(player, target, finalDmg);

                    spawnParticles(target.getLocation().add(0,1,0), combo.getParticleType(), 8, 0.3);
                    playSound(player, combo.getSoundType());
                }
            }.runTaskLater(plugin, i * 5L);
        }

        // Stun cho HEAVY_COMBO
        if (combo.getId().equals("HEAVY_COMBO") && target != null) {
            double stunChance = combo.getDouble("stun-chance", 0.3);
            if (Math.random() < stunChance) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 10));
            }
        }

        state.resetCombo();
        animationEngine.play(player, combo.getAnimationType(), combo.getParticleType());
        player.sendActionBar(color("&e&l⚡ COMBO: " + combo.getName()));

        // WeaponEXP
        state.addWeaponExp(weapon.getId(), 10L);
    }

    // ── Damage Utils ──────────────────────────────────────────────────────────

    public void dealDamage(Player attacker, LivingEntity target, double damage) {
        target.damage(damage, attacker);
    }

    public void dealDamageIgnoreArmor(Player attacker, LivingEntity target, double damage) {
        target.setHealth(Math.max(0, target.getHealth() - damage));
    }

    private LivingEntity getClosestTarget(Player player, double range) {
        LivingEntity closest = null;
        double minDist = Double.MAX_VALUE;
        for (Entity e : player.getNearbyEntities(range, range, range)) {
            if (!(e instanceof LivingEntity le) || e == player) continue;
            double d = player.getLocation().distanceSquared(e.getLocation());
            if (d < minDist) { minDist = d; closest = le; }
        }
        return closest;
    }

    private int getManaCost(WeaponData w, WeaponData.SkillData s) {
        if (s == w.getSkill1())   return w.getManaCostSkill1();
        if (s == w.getSkill2())   return w.getManaCostSkill2();
        if (s == w.getUltimate()) return w.getManaCostUltimate();
        return 0;
    }

    private void spawnParticles(Location loc, String type, int count, double spread) {
        Particle p;
        try { p = Particle.valueOf(type); } catch (Exception e) { p = Particle.CRIT; }
        if (loc.getWorld() != null)
            loc.getWorld().spawnParticle(p, loc, count, spread, spread, spread, 0);
    }

    private void spawnRingParticles(Location center, String type, double radius) {
        Particle p;
        try { p = Particle.valueOf(type); } catch (Exception e) { p = Particle.CRIT; }
        if (center.getWorld() == null) return;
        for (int i = 0; i < 36; i++) {
            double angle = Math.toRadians(i * 10);
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            center.getWorld().spawnParticle(p, x, center.getY() + 0.1, z, 3, 0, 0, 0, 0);
        }
    }

    private void playSound(Player player, String soundStr) {
        try {
            Sound sound = Sound.valueOf(soundStr);
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (Exception ignored) {}
    }

    private String skill(WeaponData.SkillData s) { return s.getParticleType(); }
    private String skill(WeaponData.ComboData  c) { return c.getParticleType(); }
    private String color(String s) { return s.replace("&", "\u00a7"); }
}
