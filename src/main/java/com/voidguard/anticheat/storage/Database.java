package com.voidguard.anticheat.storage;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.punishments.BanRecord;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * SQLite kalıcı depolama. Tüm işlemler tek bir arka plan iş parçacığında çalışır; ana iş parçacığı bloke edilmez.
 * Sürücü (org.sqlite.JDBC) Paper/Purpur sunucusuyla birlikte gelir.
 */
public final class Database {
    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws Exception;
    }

    public static final class ViolationRow {
        private final long time;
        private final String check;
        private final double vl;
        private final String evidence;
        public ViolationRow(long time, String check, double vl, String evidence) {
            this.time = time;
            this.check = check;
            this.vl = vl;
            this.evidence = evidence;
        }
        public long time() { return time; }
        public String check() { return check; }
        public double vl() { return vl; }
        public String evidence() { return evidence; }
    }

    private final VoidGuard plugin;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VoidGuard-DB");
        t.setDaemon(true);
        return t;
    });
    private Connection conn;

    public Database(VoidGuard plugin) {
        this.plugin = plugin;
    }

    public void init() throws Exception {
        plugin.getDataFolder().mkdirs();
        File f = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.file", "voidguard.db"));
        int retention = plugin.getConfig().getInt("database.violation-retention-days", 30);
        exec.submit(() -> {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath());
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA busy_timeout=5000");
                s.execute("CREATE TABLE IF NOT EXISTS bans (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, "
                        + "name TEXT, reason TEXT, check_name TEXT, evidence TEXT, type TEXT, issued_by TEXT, "
                        + "banned_at INTEGER, expires_at INTEGER, active INTEGER, unbanned_by TEXT, unbanned_at INTEGER)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_bans_uuid ON bans(uuid, active)");
                s.execute("CREATE TABLE IF NOT EXISTS violations (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, "
                        + "name TEXT, check_name TEXT, vl REAL, evidence TEXT, created_at INTEGER)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_viol_uuid ON violations(uuid, created_at)");
            }
            if (retention > 0) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM violations WHERE created_at < ?")) {
                    ps.setLong(1, System.currentTimeMillis() - retention * 86_400_000L);
                    ps.executeUpdate();
                }
            }
            return null;
        }).get(20, TimeUnit.SECONDS);
    }

    private <T> CompletableFuture<T> async(SqlSupplier<T> task) {
        CompletableFuture<T> f = new CompletableFuture<>();
        try {
            exec.execute(() -> {
                try {
                    f.complete(task.get());
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Veritabanı işlemi başarısız", t);
                    f.completeExceptionally(new CompletionException(t));
                }
            });
        } catch (Throwable t) {
            f.completeExceptionally(t);
        }
        return f;
    }

    private static BanRecord mapBan(ResultSet rs) throws Exception {
        return new BanRecord(rs.getLong("id"), UUID.fromString(rs.getString("uuid")), rs.getString("name"),
                rs.getString("reason"), rs.getString("check_name"), rs.getString("evidence"), rs.getString("type"),
                rs.getString("issued_by"), rs.getLong("banned_at"), rs.getLong("expires_at"), rs.getInt("active") == 1);
    }

    /** Başlangıçta çağrılır: aktif banları belleğe yükler. */
    public List<BanRecord> loadActiveBans() {
        try {
            return exec.submit(() -> {
                List<BanRecord> out = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM bans WHERE active = 1");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(mapBan(rs));
                }
                return out;
            }).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Aktif banlar yüklenemedi", e);
            return new ArrayList<>();
        }
    }

    public CompletableFuture<Long> insertBan(BanRecord b) {
        return async(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO bans (uuid,name,reason,check_name,evidence,type,issued_by,banned_at,expires_at,active) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, b.uuid.toString());
                ps.setString(2, b.name);
                ps.setString(3, b.reason);
                ps.setString(4, b.checkName);
                ps.setString(5, b.evidence);
                ps.setString(6, b.type);
                ps.setString(7, b.issuedBy);
                ps.setLong(8, b.bannedAt);
                ps.setLong(9, b.expiresAt);
                ps.setInt(10, b.active ? 1 : 0);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    return rs.next() ? rs.getLong(1) : -1L;
                }
            }
        });
    }

    public CompletableFuture<Void> deactivate(UUID uuid, String by, long when) {
        return async(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE bans SET active = 0, unbanned_by = ?, unbanned_at = ? WHERE uuid = ? AND active = 1")) {
                ps.setString(1, by);
                ps.setLong(2, when);
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void insertViolation(UUID uuid, String name, String check, double vl, String evidence, long time) {
        async(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO violations (uuid,name,check_name,vl,evidence,created_at) VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setString(3, check);
                ps.setDouble(4, vl);
                ps.setString(5, evidence);
                ps.setLong(6, time);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<List<ViolationRow>> recentViolations(UUID uuid, int limit) {
        return async(() -> {
            List<ViolationRow> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT created_at, check_name, vl, evidence FROM violations WHERE uuid = ? ORDER BY created_at DESC LIMIT ?")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new ViolationRow(rs.getLong(1), rs.getString(2), rs.getDouble(3), rs.getString(4)));
                    }
                }
            }
            return out;
        });
    }

    public CompletableFuture<Integer> countViolations(UUID uuid) {
        return async(() -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM violations WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    public CompletableFuture<List<BanRecord>> banHistory(UUID uuid, int limit) {
        return async(() -> {
            List<BanRecord> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM bans WHERE uuid = ? ORDER BY banned_at DESC LIMIT ?")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(mapBan(rs));
                }
            }
            return out;
        });
    }

    /** Bekleyen yazmaları tamamlayıp bağlantıyı kapatır. */
    public void close() {
        exec.shutdown();
        try {
            if (!exec.awaitTermination(8, TimeUnit.SECONDS)) exec.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (conn != null) conn.close();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Veritabanı kapatılamadı", e);
        }
    }
}
