package dev.elysium.weapon.database;

import dev.elysium.weapon.ElysiumWeapon;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WeaponDatabase - luu tru Weapon EXP va Mastery data.
 * Dung SQLite standalone, co the doi sang MySQL sau.
 * ElysiumCore chi luu PlayerData (level, balance, mana...).
 * WeaponDatabase luu weapon-specific data de gon gang.
 */
public class WeaponDatabase {

    private final ElysiumWeapon plugin;
    private Connection connection;

    public WeaponDatabase(ElysiumWeapon plugin) {
        this.plugin = plugin;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    public void initialize() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "weapon_data.db");
            String url  = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection  = DriverManager.getConnection(url);
            connection.setAutoCommit(true);

            createTables();
            plugin.getLogger().info("[WeaponDB] SQLite connected.");
        } catch (SQLException e) {
            plugin.getLogger().severe("[WeaponDB] Khong ket noi duoc database: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS weapon_mastery (
                uuid       TEXT NOT NULL,
                weapon_id  TEXT NOT NULL,
                exp        BIGINT DEFAULT 0,
                PRIMARY KEY (uuid, weapon_id)
            );
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    /** Load toan bo weapon EXP cua 1 player */
    public Map<String, Long> loadWeaponExp(UUID uuid) {
        Map<String, Long> result = new HashMap<>();
        String sql = "SELECT weapon_id, exp FROM weapon_mastery WHERE uuid = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.put(rs.getString("weapon_id"), rs.getLong("exp"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[WeaponDB] Load error: " + e.getMessage());
        }
        return result;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    /** Luu 1 weapon EXP (upsert) */
    public void saveWeaponExp(UUID uuid, String weaponId, long exp) {
        // Async de khong lag main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = """
                INSERT INTO weapon_mastery (uuid, weapon_id, exp)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid, weapon_id) DO UPDATE SET exp = excluded.exp
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, weaponId);
                ps.setLong(3, exp);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[WeaponDB] Save error: " + e.getMessage());
            }
        });
    }

    /** Luu toan bo weapon EXP cua 1 player (khi logout) */
    public void saveAllWeaponExp(UUID uuid, Map<String, Long> expMap) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = """
                INSERT INTO weapon_mastery (uuid, weapon_id, exp)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid, weapon_id) DO UPDATE SET exp = excluded.exp
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (Map.Entry<String, Long> entry : expMap.entrySet()) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, entry.getKey());
                    ps.setLong(3, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                plugin.getLogger().warning("[WeaponDB] SaveAll error: " + e.getMessage());
            }
        });
    }

    // ── Save Sync (dung khi shutdown) ────────────────────────────────────────────

    /** Save dong bo - KHONG async, dung khi server dang tat */
    public void saveAllWeaponExpSync(java.util.UUID uuid, java.util.Map<String, Long> expMap) {
        if (expMap.isEmpty()) return;
        String sql = """
            INSERT INTO weapon_mastery (uuid, weapon_id, exp)
            VALUES (?, ?, ?)
            ON CONFLICT(uuid, weapon_id) DO UPDATE SET exp = excluded.exp
        """;
        try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
            for (java.util.Map.Entry<String, Long> entry : expMap.entrySet()) {
                ps.setString(1, uuid.toString());
                ps.setString(2, entry.getKey());
                ps.setLong(3, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
            connection.commit();
        } catch (java.sql.SQLException e) {
            plugin.getLogger().warning("[WeaponDB] SaveSync error: " + e.getMessage());
        }
    }

    // ── Close ─────────────────────────────────────────────────────────────────

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("[WeaponDB] Database closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[WeaponDB] Close error: " + e.getMessage());
        }
    }
}
