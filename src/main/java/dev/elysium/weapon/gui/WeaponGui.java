package dev.elysium.weapon.gui;

import dev.elysium.weapon.ElysiumWeapon;
import dev.elysium.weapon.mastery.WeaponMastery;
import dev.elysium.weapon.weapon.PlayerWeaponState;
import dev.elysium.weapon.weapon.WeaponData;
import dev.elysium.core.gui.ElysiumGui;
import dev.elysium.core.gui.GuiButton;
import dev.elysium.core.gui.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class WeaponGui extends ElysiumGui {

    private final ElysiumWeapon plugin;
    private String selectedWeaponId = null;

    public WeaponGui(ElysiumWeapon plugin) {
        super("&5&lWeapon", 54);
        this.plugin = plugin;
    }

    @Override
    public void build(Player player) {
        fill(ItemBuilder.filler());

        WeaponData held = plugin.getWeaponManager().getHeldWeaponData(player);

        if (selectedWeaponId == null) {
            buildMain(player, held);
        } else {
            buildDetail(player, selectedWeaponId);
        }
    }

    // ── Main: vu khi dang cam + mastery overview ──────────────────────────────

    private void buildMain(Player player, WeaponData held) {
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        if (held != null) {
            int  level   = plugin.getWeaponMastery().getWeaponLevel(player, held.getId());
            long totalExp= state.getWeaponExp(held.getId());
            long expNext = plugin.getWeaponMastery().getExpForNextLevel(level);
            long expCur  = totalExp - plugin.getWeaponMastery().getExpForLevel(level);
            double pct   = expNext > 0 ? (expCur / (double) expNext) : 1.0;

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(color("&7Type: &f" + held.getType() + " &7| &f" + held.getAffinity()));
            lore.add(color("&7Dame: &f" + held.getBaseDamage()));
            lore.add("");
            lore.add(color("&6Mastery Level: &e" + level + "/50"));
            lore.add(color("&7EXP: &f" + expCur + "/" + expNext));
            lore.add(buildExpBar(pct));
            lore.add("");
            lore.add(color("&eClick de xem chi tiet!"));

            // Icon: material cua weapon
            Material mat;
            try { mat = Material.valueOf(held.getMaterial()); } catch (Exception e) { mat = Material.IRON_SWORD; }

            setButton(13, new GuiButton(
                    new ItemBuilder(mat)
                            .name(held.getDisplayName())
                            .lore(lore)
                            .customModelData(held.getModelData())
                            .glow()
                            .build(),
                    e -> {
                        e.setCancelled(true);
                        selectedWeaponId = held.getId();
                        build(player);
                        player.openInventory(getInventory());
                    }
            ));
        } else {
            fill(13, new ItemBuilder(Material.BARRIER)
                    .name("&cBan chua cam vu khi Elysium nao!")
                    .lore("&7Lay vu khi tu /wpadmin give")
                    .build());
        }

        // Stats nhanh (slot 29, 31, 33)
        if (held != null) {
            PlayerWeaponState state2 = plugin.getWeaponManager().getState(player);

            // Skill 1
            if (held.getSkill1() != null) {
                long cd = state2.getCooldownRemaining(held.getSkill1().getId());
                fill(29, new ItemBuilder(Material.BLAZE_POWDER)
                        .name(color("&b" + held.getSkill1().getName()))
                        .lore(
                            color("&7[P.Phải Giữ]"),
                            "",
                            color("&7" + held.getSkill1().getDescription()),
                            "",
                            color("&7CD: &f" + held.getSkill1().getCooldown() + "s"),
                            color("&7Mana: &b" + held.getManaCostSkill1()),
                            cd > 0 ? color("&cCooldown: " + cd + "s") : color("&aReady!")
                        ).build());
            }

            // Skill 2
            if (held.getSkill2() != null) {
                long cd = state2.getCooldownRemaining(held.getSkill2().getId());
                fill(31, new ItemBuilder(Material.BLAZE_ROD)
                        .name(color("&b" + held.getSkill2().getName()))
                        .lore(
                            color("&7[Shift + P.Phải]"),
                            "",
                            color("&7" + held.getSkill2().getDescription()),
                            "",
                            color("&7CD: &f" + held.getSkill2().getCooldown() + "s"),
                            color("&7Mana: &b" + held.getManaCostSkill2()),
                            cd > 0 ? color("&cCooldown: " + cd + "s") : color("&aReady!")
                        ).build());
            }

            // Ultimate
            if (held.getUltimate() != null) {
                long cd = state2.getCooldownRemaining(held.getUltimate().getId());
                fill(33, new ItemBuilder(Material.NETHER_STAR)
                        .name(color("&c&l" + held.getUltimate().getName()))
                        .lore(
                            color("&7[Shift + P.Trái]"),
                            "",
                            color("&7" + held.getUltimate().getDescription()),
                            "",
                            color("&7CD: &f" + held.getUltimate().getCooldown() + "s"),
                            color("&7Mana: &b" + held.getManaCostUltimate()),
                            cd > 0 ? color("&cCooldown: " + cd + "s") : color("&a&lREADY!")
                        ).glow().build());
            }
        }

        // Nut Mastery (slot 47)
        if (held != null) {
            final String wId = held.getId();
            setButton(47, new GuiButton(
                    new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                            .name(color("&6Mastery Details"))
                            .lore(color("&7Xem tien trinh unlock"))
                            .build(),
                    e -> {
                        e.setCancelled(true);
                        selectedWeaponId = wId;
                        build(player);
                        player.openInventory(getInventory());
                    }
            ));
        }

        // Nut dong (slot 49)
        setButton(49, new GuiButton(
                new ItemBuilder(Material.BARRIER).name(color("&cDong")).build(),
                e -> { e.setCancelled(true); player.closeInventory(); }
        ));
    }

    // ── Detail: mastery + unlock progress ────────────────────────────────────

    private void buildDetail(Player player, String weaponId) {
        WeaponData data = plugin.getWeaponManager().getWeaponData(weaponId);
        if (data == null) { selectedWeaponId = null; build(player); return; }

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        int  level   = plugin.getWeaponMastery().getWeaponLevel(player, weaponId);
        long totalExp= state.getWeaponExp(weaponId);
        long expNext = plugin.getWeaponMastery().getExpForNextLevel(level);
        long expCur  = totalExp - plugin.getWeaponMastery().getExpForLevel(level);
        double pct   = expNext > 0 ? (expCur / (double) expNext) : 1.0;

        // Header (slot 4)
        fill(4, new ItemBuilder(Material.NETHER_STAR)
                .name(color(data.getDisplayName() + " &7- Mastery"))
                .lore(
                    "",
                    color("&6Level: &e" + level + "/50"),
                    color("&7EXP: &f" + expCur + "/" + expNext),
                    buildExpBar(pct)
                ).glow().build());

        // Unlock milestones (slot 9-44)
        int[] milestones = {5, 10, 15, 20, 25, 30, 35, 40, 45, 50};
        int[] slots      = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30};

        for (int i = 0; i < milestones.length; i++) {
            int lv = milestones[i];
            WeaponMastery.MasteryUnlock unlock = plugin.getWeaponMastery().getUnlockAtLevel(lv);
            if (unlock == null) continue;

            boolean unlocked = level >= lv;
            Material mat = unlocked ? Material.LIME_DYE : Material.GRAY_DYE;

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(color(unlocked ? "&aDa mo khoa!" : "&7Chua mo khoa"));
            lore.add(color("&7Can level: &e" + lv));
            lore.add("");
            lore.add(color("&f" + unlock.description()));

            fill(slots[i], new ItemBuilder(mat)
                    .name(color((unlocked ? "&a✔ " : "&7○ ") + "Level " + lv))
                    .lore(lore)
                    .build());
        }

        // EXP sources info (slot 38)
        fill(38, new ItemBuilder(Material.BOOK)
                .name(color("&6Nguon EXP"))
                .lore(
                    "",
                    color("&7Dungeon:  &fx1.0"),
                    color("&7Boss:     &ex1.5"),
                    color("&7PvP:      &bx1.2"),
                    color("&7Quest:    &fx1.0"),
                    color("&6Event:    &6x2.0"),
                    "",
                    color("&7Chi nhan EXP khi dung vu khi nay!")
                ).build());

        // Passive info (slot 40)
        if (data.getPassive() != null) {
            fill(40, new ItemBuilder(Material.ENCHANTED_BOOK)
                    .name(color("&6[Noi Tai] " + data.getPassive().getId()))
                    .lore(
                        "",
                        color("&f" + data.getPassive().getDescription()),
                        "",
                        level >= 35 ? color("&aTier 2 da mo khoa!") : color("&7Tier 2 mo o Level 35")
                    ).build());
        }

        // Quay lai (slot 45)
        setButton(45, new GuiButton(
                new ItemBuilder(Material.ARROW).name(color("&7Quay Lai")).build(),
                e -> {
                    e.setCancelled(true);
                    selectedWeaponId = null;
                    build(player);
                    player.openInventory(getInventory());
                }
        ));

        // Dong (slot 49)
        setButton(49, new GuiButton(
                new ItemBuilder(Material.BARRIER).name(color("&cDong")).build(),
                e -> { e.setCancelled(true); player.closeInventory(); }
        ));
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private String buildExpBar(double pct) {
        int    bars   = 20;
        int    filled = (int) (pct * bars);
        StringBuilder bar = new StringBuilder(color("&6["));
        for (int i = 0; i < bars; i++) {
            bar.append(i < filled ? color("&e|") : color("&8|"));
        }
        bar.append(color("&6] &f" + String.format("%.1f%%", pct * 100)));
        return bar.toString();
    }

    private String color(String s) { return s.replace("&", "\u00a7"); }
}
