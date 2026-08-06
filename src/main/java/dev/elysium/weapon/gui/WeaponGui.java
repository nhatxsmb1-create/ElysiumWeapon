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

    public void open(Player player) {
        build(player);
        player.openInventory(getInventory());
        GuiListener.register(player.getUniqueId(), this);
    }

    /** Mo thang vao trang Mastery - KHONG qua overview */
    public void openMastery(Player player, String weaponId) {
        this.selectedWeaponId = weaponId;
        build(player);
        player.openInventory(getInventory());
        GuiListener.register(player.getUniqueId(), this);
    }

    @Override
    public void build(Player player) {
        fill(ItemBuilder.filler());
        if (selectedWeaponId == null) buildMain(player);
        else                          buildMastery(player, selectedWeaponId);
    }

    // ── Trang chinh: info vu khi + skill ─────────────────────────────────────

    private void buildMain(Player player) {
        WeaponData held = plugin.getWeaponManager().getHeldWeaponData(player);
        PlayerWeaponState state = plugin.getWeaponManager().getState(player);

        if (held != null) {
            int    level   = plugin.getWeaponMastery().getWeaponLevel(player, held.getId());
            long   totalExp= state.getWeaponExp(held.getId());
            long   expNext = plugin.getWeaponMastery().getExpForNextLevel(level);
            long   expCur  = totalExp - plugin.getWeaponMastery().getExpForLevel(level);
            double pct     = expNext > 0 ? (expCur / (double) expNext) : 1.0;

            Material mat;
            try { mat = Material.valueOf(held.getMaterial()); }
            catch (Exception e) { mat = Material.IRON_SWORD; }

            List<String> lore = new ArrayList<>();
            lore.add(color("&8" + held.getType() + " | " + held.getAffinity()));
            lore.add("");
            lore.add(color("&7Dame: &f" + held.getBaseDamage()));
            lore.add("");
            lore.add(color("&6✦ Mastery Level: " + getLevelColor(level) + level + " &7/ 50"));
            lore.add(color("&7EXP: &f" + expCur + " / " + expNext));
            lore.add(buildExpBar(pct));
            lore.add("");
            lore.add(color("&eClick de xem Mastery chi tiet!"));

            setButton(13, new GuiButton(
                    new ItemBuilder(mat)
                            .name(color(held.getDisplayName()))
                            .lore(lore)
                            .customModelData(held.getModelData())
                            .glow().build(),
                    e -> {
                        e.setCancelled(true);
                        selectedWeaponId = held.getId();
                        build(player);
                        player.openInventory(getInventory());
                    }
            ));

            // Skill 1 (slot 29)
            if (held.getSkill1() != null) {
                long cd = state.getCooldownRemaining(held.getSkill1().getId());
                fill(29, new ItemBuilder(Material.BLAZE_POWDER)
                        .name(color("&b" + held.getSkill1().getName()))
                        .lore(
                            color("&7[P.Phải Giữ]"), "",
                            color("&7" + held.getSkill1().getDescription()), "",
                            color("&7CD: &f" + held.getSkill1().getCooldown() + "s"),
                            color("&7Mana: &b" + held.getManaCostSkill1()),
                            cd > 0 ? color("&cCooldown: " + cd + "s") : color("&aReady!")
                        ).build());
            }

            // Skill 2 (slot 31)
            if (held.getSkill2() != null) {
                long cd = state.getCooldownRemaining(held.getSkill2().getId());
                fill(31, new ItemBuilder(Material.BLAZE_ROD)
                        .name(color("&b" + held.getSkill2().getName()))
                        .lore(
                            color("&7[Shift + P.Phải]"), "",
                            color("&7" + held.getSkill2().getDescription()), "",
                            color("&7CD: &f" + held.getSkill2().getCooldown() + "s"),
                            color("&7Mana: &b" + held.getManaCostSkill2()),
                            cd > 0 ? color("&cCooldown: " + cd + "s") : color("&aReady!")
                        ).build());
            }

            // Ultimate (slot 33)
            if (held.getUltimate() != null) {
                long cd = state.getCooldownRemaining(held.getUltimate().getId());
                fill(33, new ItemBuilder(Material.NETHER_STAR)
                        .name(color("&c&l" + held.getUltimate().getName()))
                        .lore(
                            color("&7[Shift + P.Trái]"), "",
                            color("&7" + held.getUltimate().getDescription()), "",
                            color("&7CD: &f" + held.getUltimate().getCooldown() + "s"),
                            color("&7Mana: &b" + held.getManaCostUltimate()),
                            cd > 0 ? color("&cCooldown: " + cd + "s") : color("&a&lREADY!")
                        ).glow().build());
            }

            // Mastery button (slot 47)
            final String wId = held.getId();
            setButton(47, new GuiButton(
                    new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                            .name(color("&6✦ Xem Mastery"))
                            .lore(color("&7Click de xem tien trinh unlock")).build(),
                    e -> {
                        e.setCancelled(true);
                        selectedWeaponId = wId;
                        build(player);
                        player.openInventory(getInventory());
                    }
            ));

        } else {
            fill(13, new ItemBuilder(Material.BARRIER)
                    .name(color("&cBan chua cam vu khi Elysium nao!"))
                    .lore(color("&7Lay vu khi tu /wpadmin give")).build());
        }

        setButton(49, new GuiButton(
                new ItemBuilder(Material.BARRIER).name(color("&cDong")).build(),
                e -> { e.setCancelled(true); player.closeInventory(); }
        ));
    }

    // ── Trang Mastery: 1 trang duy nhat, dep, ro rang ────────────────────────

    private void buildMastery(Player player, String weaponId) {
        WeaponData data = plugin.getWeaponManager().getWeaponData(weaponId);
        if (data == null) { selectedWeaponId = null; build(player); return; }

        PlayerWeaponState state = plugin.getWeaponManager().getState(player);
        int    level   = plugin.getWeaponMastery().getWeaponLevel(player, weaponId);
        long   totalExp= state.getWeaponExp(weaponId);
        long   expNext = plugin.getWeaponMastery().getExpForNextLevel(level);
        long   expCur  = totalExp - plugin.getWeaponMastery().getExpForLevel(level);
        double pct     = expNext > 0 ? (expCur / (double) expNext) : 1.0;

        // Vien tren (row 0): thong tin weapon
        Material wMat;
        try { wMat = Material.valueOf(data.getMaterial()); }
        catch (Exception e) { wMat = Material.IRON_SWORD; }

        // Slot 0: Weapon icon + EXP
        fill(0, new ItemBuilder(wMat)
                .name(color(data.getDisplayName()))
                .lore(
                    color("&6✦ Mastery Level: " + getLevelColor(level) + level + " &7/ 50"),
                    buildExpBar(pct),
                    color("&7" + expCur + " / " + expNext + " EXP"),
                    color("&7Tong EXP: &f" + totalExp)
                ).customModelData(data.getModelData()).glow().build());

        // Slot 4: Progress visual
        fill(4, new ItemBuilder(Material.PAPER)
                .name(color("&e" + level + " &7/ 50 &8(" + String.format("%.1f%%", (level/50.0)*100) + ")"))
                .lore(
                    buildLargeExpBar(pct),
                    color("&7" + expCur + " / " + expNext + " EXP den cap tiep theo")
                ).build());

        // Slot 8: Noi tai
        if (data.getPassive() != null) {
            fill(8, new ItemBuilder(Material.ENCHANTED_BOOK)
                    .name(color("&6⚡ Noi Tai: " + data.getPassive().getId()))
                    .lore(
                        "",
                        color("&f" + data.getPassive().getDescription()),
                        "",
                        color(level >= 35 ? "&a✔ Tier 2 da mo!" : "&7Tier 2 mo o Level 35")
                    ).build());
        }

        // Vien ngang (row 1, slot 9-17): nen den
        for (int i = 9; i < 18; i++)
            fill(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(color("&r")).build());

        // ── Milestone: 10 moc, 2 hang x 5 cot ───────────────────────────────
        // Hang 1 (row 2): Lv 5,10,15,20,25 -> slot 19,20,21,22,23
        // Hang 2 (row 3): Lv30,35,40,45,50 -> slot 28,29,30,31,32
        int[]   milestones = {5, 10, 15, 20, 25, 30, 35, 40, 45, 50};
        int[]   mSlots     = {19,20, 21, 22, 23, 28, 29, 30, 31, 32};

        for (int i = 0; i < milestones.length; i++) {
            int lv = milestones[i];
            WeaponMastery.MasteryUnlock unlock = plugin.getWeaponMastery().getUnlockAtLevel(lv);
            if (unlock == null) continue;

            boolean unlocked  = level >= lv;
            boolean isCurrent = !unlocked && (i == 0 || level >= milestones[i-1]);

            Material mat = unlocked      ? Material.LIME_STAINED_GLASS_PANE
                         : isCurrent     ? Material.YELLOW_STAINED_GLASS_PANE
                         :                 Material.GRAY_STAINED_GLASS_PANE;

            String statusIcon  = unlocked ? "&a✔ " : isCurrent ? "&e◆ " : "&8○ ";
            String statusText  = unlocked ? "&aDa mo khoa" : isCurrent ? "&eGan mo khoa!" : "&8Chua mo";
            String levelColor  = unlocked ? "&a" : isCurrent ? "&e" : "&8";

            // Tach description thanh nhieu dong neu dai
            String desc = unlock.description();

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(color(statusText));
            if (!unlocked) lore.add(color("&7Can: &e" + lv + " &7(Hien tai: &f" + level + "&7)"));
            lore.add("");
            // Mo khoa: hien dep hon
            lore.add(color("&7Mo khoa:"));
            lore.add(color("&f  " + desc));
            if (unlocked) lore.add(color(""));
            if (unlocked) lore.add(color("&a✦ Dang hoat dong!"));

            fill(mSlots[i], new ItemBuilder(mat)
                    .name(color(statusIcon + levelColor + "Level " + lv))
                    .lore(lore)
                    .build());
        }

        // Vien ngang (row 4, slot 36-44): nen den
        for (int i = 36; i < 45; i++)
            fill(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(color("&r")).build());

        // Slot 36: Nguon EXP
        fill(36, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name(color("&aNguon EXP"))
                .lore(
                    "",
                    color("&7⚔ Dungeon:  &fx1.0"),
                    color("&c☠ Boss:     &ex1.5"),
                    color("&b⚔ PvP:      &bx1.2"),
                    color("&7Quest:      &fx1.0"),
                    color("&6★ Event:    &6x2.0")
                ).build());

        // Quay lai (slot 45)
        setButton(45, new GuiButton(
                new ItemBuilder(Material.ARROW).name(color("&7\u2190 Quay Lai")).build(),
                e -> {
                    e.setCancelled(true);
                    selectedWeaponId = null;
                    build(player);
                    player.openInventory(getInventory());
                }
        ));

        // Dong (slot 53)
        setButton(53, new GuiButton(
                new ItemBuilder(Material.BARRIER).name(color("&cDong")).build(),
                e -> { e.setCancelled(true); player.closeInventory(); }
        ));
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private String getLevelColor(int level) {
        if (level >= 50) return "&6&l";
        if (level >= 35) return "&a";
        if (level >= 20) return "&e";
        if (level >= 10) return "&f";
        return "&7";
    }

    private String buildExpBar(double pct) {
        int bars   = 15;
        int filled = (int) (pct * bars);
        StringBuilder bar = new StringBuilder(color("&6["));
        for (int i = 0; i < bars; i++)
            bar.append(i < filled ? color("&e|") : color("&8|"));
        bar.append(color("&6] &f" + String.format("%.1f%%", pct * 100)));
        return bar.toString();
    }

    private String buildLargeExpBar(double pct) {
        int bars   = 20;
        int filled = (int) (pct * bars);
        StringBuilder bar = new StringBuilder(color("&6["));
        for (int i = 0; i < bars; i++)
            bar.append(i < filled ? color("&e\u2588") : color("&8\u2591"));
        bar.append(color("&6]"));
        return bar.toString();
    }

    private String color(String s) { return s.replace("&", "\u00a7"); }
}
