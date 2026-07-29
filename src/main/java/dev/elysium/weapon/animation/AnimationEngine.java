package dev.elysium.weapon.animation;

import dev.elysium.weapon.ElysiumWeapon;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * AnimationEngine: xu ly hieu ung visual cho skill/combo.
 * Phase 1: dung Particle thuan.
 * Phase 2: them ProtocolLib packet cho animation phuc tap.
 */
public class AnimationEngine {

    private final ElysiumWeapon plugin;

    public AnimationEngine(ElysiumWeapon plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, String animationType, String particleType) {
        Particle particle;
        try { particle = Particle.valueOf(particleType); }
        catch (Exception e) { particle = Particle.CRIT; }

        switch (animationType) {
            case "DASH_FORWARD"    -> animDashForward(player, particle);
            case "SPIN_ATTACK"     -> animSpinAttack(player, particle);
            case "SAKURA_ULTIMATE" -> animSakura(player, particle);
            case "GROUND_SLAM"     -> animGroundSlam(player, particle);
            case "WAR_CRY"         -> animWarCry(player, particle);
            case "TITAN_FALL"      -> animTitanFall(player, particle);
            case "PROJECTILE_LAUNCH"-> animProjectile(player, particle);
            case "NOVA_EXPLODE"    -> animNova(player, particle);
            case "ABSOLUTE_ZERO"   -> animAbsoluteZero(player, particle);
            case "ARROW_LAUNCH"    -> animArrow(player, particle);
            case "ARROW_RAIN"      -> animArrowRain(player, particle);
            case "CHARGE_SHOT"     -> animChargeShot(player, particle);
            case "SHADOW_TELEPORT" -> animShadowTeleport(player, particle);
            case "BLADE_STORM"     -> animBladeStorm(player, particle);
            case "ISSEN"           -> animIssen(player, particle);
            case "TRIPLE_SLASH"    -> animTripleSlash(player, particle);
            case "HEAVY_COMBO"     -> animHeavyCombo(player, particle);
            case "BARRAGE"         -> animBarrage(player, particle);
            case "RAPID_ARROWS"    -> animRapidArrows(player, particle);
            case "IAIDO_COMBO"     -> animIaido(player, particle);
        }
    }

    // ── Warrior Animations ────────────────────────────────────────────────────

    private void animDashForward(Player p, Particle particle) {
        new BukkitRunnable() {
            int tick = 0;
            Location loc = p.getLocation().clone();
            Vector dir   = loc.getDirection().normalize();
            @Override public void run() {
                if (tick++ > 6) { cancel(); return; }
                loc.add(dir);
                spawnLine(p.getWorld(), particle, loc, 5, 0.3);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void animSpinAttack(Player p, Particle particle) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= 20) { cancel(); return; }
                double angle = Math.toRadians(tick * 18);
                double r     = 2.0;
                double x     = p.getLocation().getX() + r * Math.cos(angle);
                double z     = p.getLocation().getZ() + r * Math.sin(angle);
                p.getWorld().spawnParticle(particle, x, p.getLocation().getY() + 1, z, 3, 0, 0, 0, 0);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void animSakura(Player p, Particle particle) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick++ > 40) { cancel(); return; }
                for (int i = 0; i < 5; i++) {
                    double angle = Math.toRadians(tick * 9 + i * 72);
                    double r     = tick * 0.2;
                    double x     = p.getLocation().getX() + r * Math.cos(angle);
                    double z     = p.getLocation().getZ() + r * Math.sin(angle);
                    p.getWorld().spawnParticle(particle, x, p.getLocation().getY() + 1.5, z, 2, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void animGroundSlam(Player p, Particle particle) {
        Location loc = p.getLocation();
        for (int r = 1; r <= 5; r++) {
            final int radius = r;
            new BukkitRunnable() {
                @Override public void run() {
                    for (int i = 0; i < 24; i++) {
                        double angle = Math.toRadians(i * 15);
                        double x     = loc.getX() + radius * Math.cos(angle);
                        double z     = loc.getZ() + radius * Math.sin(angle);
                        loc.getWorld().spawnParticle(particle, x, loc.getY() + 0.1, z, 1, 0, 0, 0, 0);
                    }
                }
            }.runTaskLater(plugin, radius * 2L);
        }
    }

    private void animWarCry(Player p, Particle particle) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick++ > 20) { cancel(); return; }
                double r = tick * 0.4;
                for (int i = 0; i < 12; i++) {
                    double angle = Math.toRadians(i * 30);
                    double x = p.getLocation().getX() + r * Math.cos(angle);
                    double z = p.getLocation().getZ() + r * Math.sin(angle);
                    p.getWorld().spawnParticle(particle, x, p.getLocation().getY() + 1, z, 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void animTitanFall(Player p, Particle particle) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick++ > 30) { cancel(); return; }
                p.getWorld().spawnParticle(particle,
                        p.getLocation().getX(), p.getLocation().getY() + tick * 0.2,
                        p.getLocation().getZ(), 5, 0.5, 0, 0.5, 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void animTripleSlash(Player p, Particle particle) {
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            new BukkitRunnable() {
                @Override public void run() {
                    Vector dir = p.getLocation().getDirection().normalize();
                    Location front = p.getLocation().add(dir.multiply(1.5 + idx));
                    spawnLine(p.getWorld(), particle, front, 8, 0.8);
                }
            }.runTaskLater(plugin, i * 5L);
        }
    }

    private void animHeavyCombo(Player p, Particle particle) {
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            new BukkitRunnable() {
                @Override public void run() {
                    Location loc = p.getLocation().add(p.getLocation().getDirection().multiply(2));
                    p.getWorld().spawnParticle(particle, loc, 20, 1.0, 1.0, 1.0, 0);
                }
            }.runTaskLater(plugin, i * 8L);
        }
    }

    // ── Mage Animations ───────────────────────────────────────────────────────

    private void animProjectile(Player p, Particle particle) {
        p.getWorld().spawnParticle(particle, p.getEyeLocation(), 10, 0.2, 0.2, 0.2, 0);
    }

    private void animNova(Player p, Particle particle) {
        new BukkitRunnable() {
            double r = 0;
            @Override public void run() {
                if (r > 4) { cancel(); return; }
                for (int i = 0; i < 24; i++) {
                    double angle = Math.toRadians(i * 15);
                    double x = p.getLocation().getX() + r * Math.cos(angle);
                    double z = p.getLocation().getZ() + r * Math.sin(angle);
                    p.getWorld().spawnParticle(particle, x, p.getLocation().getY() + 0.5, z, 2, 0, 0, 0, 0);
                }
                r += 0.5;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void animAbsoluteZero(Player p, Particle particle) {
        new BukkitRunnable() {
            int tick = 0;
            Location center = p.getLocation().clone();
            @Override public void run() {
                if (tick++ > 100) { cancel(); return; }
                for (int i = 0; i < 6; i++) {
                    double angle = Math.toRadians(tick * 6 + i * 60);
                    double x = center.getX() + 6 * Math.cos(angle);
                    double z = center.getZ() + 6 * Math.sin(angle);
                    center.getWorld().spawnParticle(particle, x, center.getY() + 1, z, 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Ranger Animations ─────────────────────────────────────────────────────

    private void animArrow(Player p, Particle particle) {
        Vector dir = p.getLocation().getDirection().normalize();
        Location loc = p.getEyeLocation();
        for (int i = 0; i < 5; i++) {
            loc.add(dir);
            p.getWorld().spawnParticle(particle, loc, 3, 0.1, 0.1, 0.1, 0);
        }
    }

    private void animArrowRain(Player p, Particle particle) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick++ > 60) { cancel(); return; }
                Location loc = p.getLocation().add(
                        (Math.random() - 0.5) * 12, 6, (Math.random() - 0.5) * 12);
                p.getWorld().spawnParticle(particle, loc, 2, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void animChargeShot(Player p, Particle particle) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick++ > 60 || !p.isOnline()) { cancel(); return; }
                double r = (tick / 60.0) * 2.0;
                for (int i = 0; i < 8; i++) {
                    double angle = Math.toRadians(tick * 6 + i * 45);
                    double x = p.getLocation().getX() + r * Math.cos(angle);
                    double z = p.getLocation().getZ() + r * Math.sin(angle);
                    p.getWorld().spawnParticle(particle, x, p.getLocation().getY() + 1.2, z, 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void animRapidArrows(Player p, Particle particle) {
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            new BukkitRunnable() {
                @Override public void run() {
                    p.getWorld().spawnParticle(particle, p.getEyeLocation(), 5, 0.1, 0.1, 0.1, 0);
                }
            }.runTaskLater(plugin, i * 4L);
        }
    }

    // ── Assassin Animations ───────────────────────────────────────────────────

    private void animShadowTeleport(Player p, Particle particle) {
        Location from = p.getLocation().clone();
        p.getWorld().spawnParticle(particle, from, 30, 0.3, 1, 0.3, 0);
        new BukkitRunnable() {
            @Override public void run() {
                p.getWorld().spawnParticle(particle, p.getLocation(), 30, 0.3, 1, 0.3, 0);
            }
        }.runTaskLater(plugin, 2L);
    }

    private void animBladeStorm(Player p, Particle particle) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick++ > 40) { cancel(); return; }
                double angle = Math.toRadians(tick * 9);
                double x = p.getLocation().getX() + 1.5 * Math.cos(angle);
                double z = p.getLocation().getZ() + 1.5 * Math.sin(angle);
                p.getWorld().spawnParticle(particle, x, p.getLocation().getY() + 1, z, 3, 0.1, 0.1, 0.1, 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void animIssen(Player p, Particle particle) {
        p.getWorld().spawnParticle(particle, p.getLocation(), 50, 0.5, 1, 0.5, 0.1);
        new BukkitRunnable() {
            @Override public void run() {
                p.getWorld().spawnParticle(particle, p.getLocation(), 30, 0.3, 1, 0.3, 0.05);
            }
        }.runTaskLater(plugin, 20L);
    }

    private void animBarrage(Player p, Particle particle) {
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            new BukkitRunnable() {
                @Override public void run() {
                    p.getWorld().spawnParticle(particle, p.getEyeLocation(), 8, 0.2, 0.2, 0.2, 0);
                }
            }.runTaskLater(plugin, i * 5L);
        }
    }

    private void animIaido(Player p, Particle particle) {
        // Nhat 1: nhe
        p.getWorld().spawnParticle(particle, p.getLocation().add(p.getLocation().getDirection().multiply(2)), 5, 0.5, 0.5, 0.5, 0);
        // Nhat 2: manh (delayed)
        new BukkitRunnable() {
            @Override public void run() {
                Location slash = p.getLocation().add(p.getLocation().getDirection().multiply(2.5));
                p.getWorld().spawnParticle(particle, slash, 30, 1.0, 0.5, 1.0, 0.1);
                p.getWorld().playEffect(slash, Effect.STEP_SOUND, Material.WHITE_CONCRETE);
            }
        }.runTaskLater(plugin, 5L);
        // Nhat 3: nhe
        new BukkitRunnable() {
            @Override public void run() {
                p.getWorld().spawnParticle(particle, p.getLocation().add(p.getLocation().getDirection()), 5, 0.5, 0.5, 0.5, 0);
            }
        }.runTaskLater(plugin, 10L);
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private void spawnLine(World world, Particle particle, Location center, int count, double spread) {
        world.spawnParticle(particle, center, count, spread, spread, spread, 0);
    }
}
