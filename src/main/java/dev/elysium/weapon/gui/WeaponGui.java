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

    /**
     * 🔑 HÀM MỞ GUI AN TOÀN: 
     * Mở inventory trước -> Đợi Bukkit dọn InventoryCloseEvent cũ -> Đăng ký GuiListener ngay sau đó.
     */
    public void open(Player player) {
        build(player);
        player.openInventory(getInventory());
        GuiListener.register(player.getUniqueId(), this);
    }

    /** Mo thang vao trang Mastery cua weapon cu the */
    public void openMastery(Player player, String weaponId) {
        this.selectedWeaponId = weaponId;
        GuiListener.register(player.getUniqueId(), this);
        open(player);
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
                        open(player); // 👈 Sửa: Dùng open(player)
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
                        open(player); // 👈 Sửa: Dùng open(player)
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

        // Fill nen
        fill(ItemBuilder.filler());

        // ── Row 1: Header info ────────────────────────────────────────────────

        // Weapon icon (slot 4)
        Material wMat;
        try { wMat = Material.valueOf(data.getMaterial()); } catch (Exception e) { wMat = Material.IRON_SWORD; }
        fill(4, new ItemBuilder(wMat)
                .name(color(data.getDisplayName()))
                .lore(
                    color("&8Mastery Level"),
                    "",
                    color("&6✦ Level: " + getLevelColor(level) + level + " &7/ 50"),
                    buildExpBar(pct),
                    color("&7" + expCur + " / " + expNext + " EXP"),
                    "",
                    color("&7Tổng EXP: &f" + totalExp)
                ).customModelData(data.getModelData()).glow().build());

        // Passive (slot 2)
        if (data.getPassive() != null) {
            fill(2, new ItemBuilder(Material.ENCHANTED_BOOK)
                    .name(color("&6⚡ Nội Tại"))
                    .lore(
                        "",
                        color("&f" + data.getPassive().getDescription()),
                        "",
                        color(level >= 35 ? "&a✔ Tier 2 đã mở!" : "&7Tier 2 mở ở Level 35")
                    ).build());
        }

        // EXP nguon (slot 6)
        fill(6, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name(color("&aNguồn EXP"))
                .lore(
                    "",
                    color("&7⚔ Dungeon:  &fx1.0"),
                    color("&c☠ Boss:     &ex1.5"),
                    color("&b⚔ PvP:      &bx1.2"),
                    color("&7📜 Quest:    &fx1.0"),
                    color("&6★ Event:    &6x2.0")
                ).build());

        // ── Divider ───────────────────────────────────────────────────────────
        for (int i = 9; i < 18; i++) {
            fill(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(color("&r")).build());
        }

        // ── Milestone Grid: 5 slots x 2 rows ─────────────────────────────────
        // Row 1: Lv 5, 10, 15, 20, 25  -> slots 19,20,21,22,23
        // Row 2: Lv 30,35, 40, 45, 50  -> slots 28,29,30,31,32
        int[] milestones = {5, 10, 15, 20, 25, 30, 35, 40, 45, 50};
        int[] mSlots     = {19, 20, 21, 22, 23, 28, 29, 30, 31, 32};

        for (int i = 0; i < milestones.length; i++) {
            int lv = milestones[i];
            WeaponMastery.MasteryUnlock unlock = plugin.getWeaponMastery().getUnlockAtLevel(lv);
            if (unlock == null) continue;

            boolean unlocked  = level >= lv;
            boolean isCurrent = !unlocked && (i == 0 || level >= milestones[i-1]);

            // Material theo trang thai
            Material mat;
            if (unlocked)       mat = Material.LIME_STAINED_GLASS_PANE;
            else if (isCurrent) mat = Material.YELLOW_STAINED_GLASS_PANE;
            else                mat = Material.GRAY_STAINED_GLASS_PANE;

            // Icon tren milestone
            String icon = unlocked ? "&a✔" : isCurrent ? "&e◆" : "&8○";
            String nameColor = unlocked ? "&a" : isCurrent ? "&e" : "&7";

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(color(unlocked ? "&aDã mở khóa!" : isCurrent ? "&eGần mở khóa!" : "&8Chưa mở khóa"));
            lore.add(color("&7Cần level: &e" + lv));
            lore.add("");
            // Description dep hon
            String[] descParts = unlock.description().split(" - ", 2);
            if (descParts.length > 1) {
                lore.add(color("&7" + descParts[0] + ":"));
                lore.add(color("&f  " + descParts[1]));
            } else {
                lore.add(color("&f" + unlock.description()));
            }

            // Milestone icon o slot phia tren
            int iconSlot = mSlots[i] - 9; // Row tren milestone
            fill(iconSlot, new ItemBuilder(unlocked ? Material.LIME_DYE : isCurrent ? Material.YELLOW_DYE : Material.GRAY_DYE)
                    .name(color(icon + " &8Lv." + lv)).build());

            fill(mSlots[i], new ItemBuilder(mat)
                    .name(color(icon + " " + nameColor + "Level " + lv))
                    .lore(lore)
                    .build());
        }

        // ── Bottom bar ────────────────────────────────────────────────────────
        for (int i = 36; i < 45; i++) {
            fill(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(color("&r")).build());
        }

        // Quay lai (slot 45)
        setButton(45, new GuiButton(
                new ItemBuilder(Material.ARROW).name(color("&7← Quay Lại")).build(),
                e -> {
                    e.setCancelled(true);
                    selectedWeaponId = null;
                    build(player);
                    player.openInventory(getInventory());
                }
        ));

        // Progress tong (slot 49)
        fill(49, new ItemBuilder(Material.PAPER)
                .name(color("&f" + level + " &7/ 50 &8(" + String.format("%.1f%%", (level / 50.0) * 100) + ")"))
                .lore(color("&7Tổng tiến trình Mastery")).build());

        // Dong (slot 53)
        setButton(53, new GuiButton(
                new ItemBuilder(Material.BARRIER).name(color("&cĐóng")).build(),
                e -> { e.setCancelled(true); player.closeInventory(); }
        ));
    }

    private String getLevelColor(int level) {
        if (level >= 50) return "&6&l";
        if (level >= 35) return "&a";
        if (level >= 20) return "&e";
        if (level >= 10) return "&f";
        return "&7";
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
