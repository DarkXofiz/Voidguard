package com.voidguard.anticheat.punishments;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.checks.Check;
import com.voidguard.anticheat.data.PlayerData;
import com.voidguard.anticheat.punishments.ActionDecider.Decision;
import com.voidguard.anticheat.punishments.ActionDecider.Level;
import com.voidguard.anticheat.punishments.ActionDecider.Thresholds;
import com.voidguard.anticheat.punishments.DetectionLedger.Outcome;
import com.voidguard.anticheat.punishments.DetectionLedger.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** Şüphe (flag) kayıtlarını VL'ye çevirir, loglar, yetkililere bildirir ve ceza kararını tetikler. */
public final class ViolationManager {
    private final VoidGuard plugin;
    private final Set<UUID> alertsOff = ConcurrentHashMap.newKeySet();
    /** Çıkışta silinmez: oyuncu tespitler arasında çıkıp girerek sayacı sıfırlayamaz. Süre dolunca temizlenir. */
    private final Map<UUID, DetectionLedger> ledgers = new ConcurrentHashMap<>();
    private long lastLedgerSweep;
    private volatile Thresholds thresholds = new Thresholds(10, 40, 2, 4.0);
    private volatile double decay = 3.0;
    private volatile long alertCooldownMs = 3000;
    private volatile long logIntervalMs = 3000;
    private volatile boolean logToDb = true;
    private volatile boolean consoleAlerts = true;
    private volatile boolean debug = false;
    private volatile int detectionsRequired = 3;
    private volatile long detectionMinIntervalMs = 1500;
    private volatile long detectionWindowMs = 600_000;

    public ViolationManager(VoidGuard plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        thresholds = new Thresholds(c.getDouble("violations.alert-vl", 10.0), c.getDouble("violations.punish-vl", 40.0),
                c.getInt("violations.min-distinct-checks", 2), c.getDouble("violations.single-check-multiplier", 4.0));
        decay = c.getDouble("violations.decay-per-minute", 3.0);
        alertCooldownMs = c.getLong("violations.alert-cooldown-ms", 3000);
        logIntervalMs = c.getLong("logging.db-log-interval-ms", 3000);
        logToDb = c.getBoolean("logging.log-violations", true);
        consoleAlerts = c.getBoolean("alerts.console", true);
        debug = c.getBoolean("logging.debug", false);
        detectionsRequired = Math.max(1, c.getInt("detections.required", 3));
        detectionMinIntervalMs = Math.max(0, c.getLong("detections.min-interval-ms", 1500));
        detectionWindowMs = Math.max(1, c.getLong("detections.window-seconds", 600)) * 1000L;
    }

    public double decayPerMinute() {
        return decay;
    }

    public Thresholds thresholds() {
        return thresholds;
    }

    public boolean toggleAlerts(UUID id) {
        if (alertsOff.remove(id)) return true;
        alertsOff.add(id);
        return false;
    }

    public void forget(UUID id) {
        alertsOff.remove(id);
    }

    /**
     * Bir kontrolün şüphe bildirimi. Yalnızca ana iş parçacığından çağrılmalıdır.
     * Her SAYILAN tespit anında loglanır ve yetkililere bildirilir; kontrol başına {@code detections.required}
     * (varsayılan 3) sayılan tespit tamamlanır tamamlanmaz ceza tetiklenir.
     */
    public void flag(Player p, Check check, double amount, String evidence) {
        if (!plugin.getConfig().getBoolean("enabled", true)) return;
        UUID id = p.getUniqueId();
        if (plugin.effect().isActive(id) || plugin.punishments().isPunishing(id)) return;
        PlayerData d = plugin.data().get(p);
        long now = System.currentTimeMillis();
        String name = check.getName();
        double vl = d.tracker.add(name, amount, now, decay, evidence);

        if (debug) plugin.getLogger().info("[DEBUG] " + p.getName() + " " + name + " vl=" + fmt(vl) + " " + evidence);

        sweepLedgers(now);
        Outcome o = ledgers.computeIfAbsent(id, k -> new DetectionLedger())
                .record(name, now, detectionMinIntervalMs, detectionWindowMs, detectionsRequired);

        if (o.counted()) {
            // Gerçek tespit: bekletmeden logla (kuyruk iş parçacığına), konsola yaz, yetkililere bildir.
            String ev = evidence == null ? "-" : evidence;
            plugin.getLogger().warning("TESPİT: " + p.getName() + " (" + id + ") " + name + " " + o.count() + "/"
                    + detectionsRequired + " VL " + fmt(vl) + " | " + ev);
            if (logToDb) {
                d.logCooldown.put(name, now);
                plugin.db().insertViolation(id, p.getName(), name, vl,
                        "[" + o.count() + "/" + detectionsRequired + "] " + ev, now);
            }
            notifyDetection(p, name, o.count(), vl, ev);
        } else if (logToDb && now - d.logCooldown.getOrDefault(name, 0L) >= logIntervalMs) {
            // Aynı olayın tekrarları: yalnızca seyrek kayıt (yüzlerce satırı önler)
            d.logCooldown.put(name, now);
            plugin.db().insertViolation(id, p.getName(), name, vl, evidence, now);
        }

        if (o.result() == Result.THRESHOLD) {
            ledgers.remove(id);
            plugin.punishments().trigger(p, new Decision(Level.PUNISH, true, name, vl, 1), summary(d, now));
        }
    }

    /** Doğrulanmış tespit bildirimi: voidguard.alerts izni olan çevrimiçi yetkililere (alerts kapalıysa gönderilmez). */
    private void notifyDetection(Player p, String check, int count, double vl, String evidence) {
        List<String> lines = plugin.msgLines("detection", "{oyuncu}", p.getName(), "{hile}", check,
                "{sayi}", String.valueOf(count), "{gerekli}", String.valueOf(detectionsRequired),
                "{vl}", fmt(vl), "{kanit}", evidence);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (alertsOff.contains(staff.getUniqueId())) continue;
            if (!staff.hasPermission("voidguard.alerts") && !staff.hasPermission("voidguard.admin")) continue;
            for (String l : lines) staff.sendMessage(l);
        }
    }

    private void sweepLedgers(long now) {
        if (now - lastLedgerSweep < 60_000) return;
        lastLedgerSweep = now;
        ledgers.entrySet().removeIf(e -> e.getValue().idle(now, detectionWindowMs));
    }

    /** Ceza dry-run'da veya elle sıfırlandığında tespit sayaçlarını temizler. */
    public void resetDetections(UUID id) {
        DetectionLedger l = ledgers.remove(id);
        if (l != null) l.clear();
    }

    /** Ceza kaydı için tetiklenen kontrollerin ve kanıtların kısa özeti. */
    public String summary(PlayerData d, long now) {
        Map<String, Double> snap = d.tracker.snapshot(now, decay);
        List<Map.Entry<String, Double>> list = new ArrayList<>(snap.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Double> e : list) {
            if (n++ >= 4) break;
            sb.append(e.getKey()).append(" (VL ").append(fmt(e.getValue())).append("): ");
            sb.append(String.join(" | ", d.tracker.evidence(e.getKey()))).append("; ");
        }
        return sb.length() > 900 ? sb.substring(0, 900) : sb.toString();
    }

    public static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
