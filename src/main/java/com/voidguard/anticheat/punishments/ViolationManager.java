package com.voidguard.anticheat.punishments;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.checks.Check;
import com.voidguard.anticheat.data.PlayerData;
import com.voidguard.anticheat.punishments.ActionDecider.Decision;
import com.voidguard.anticheat.punishments.ActionDecider.Level;
import com.voidguard.anticheat.punishments.ActionDecider.Thresholds;
import com.voidguard.anticheat.util.ServerUtil;
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
    private volatile Thresholds thresholds = new Thresholds(10, 40, 2, 4.0);
    private volatile double decay = 3.0;
    private volatile long alertCooldownMs = 3000;
    private volatile long logIntervalMs = 3000;
    private volatile boolean logToDb = true;
    private volatile boolean consoleAlerts = true;
    private volatile boolean debug = false;

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

    /** Bir kontrolün şüphe bildirimi. Yalnızca ana iş parçacığından çağrılmalıdır. */
    public void flag(Player p, Check check, double amount, String evidence) {
        if (!plugin.getConfig().getBoolean("enabled", true)) return;
        UUID id = p.getUniqueId();
        if (plugin.effect().isActive(id) || plugin.punishments().isPunishing(id)) return;
        PlayerData d = plugin.data().get(p);
        long now = System.currentTimeMillis();
        String name = check.getName();
        double vl = d.tracker.add(name, amount, now, decay, evidence);

        if (debug) plugin.getLogger().info("[DEBUG] " + p.getName() + " " + name + " vl=" + fmt(vl) + " " + evidence);

        if (logToDb && now - d.logCooldown.getOrDefault(name, 0L) >= logIntervalMs) {
            d.logCooldown.put(name, now);
            plugin.db().insertViolation(id, p.getName(), name, vl, evidence, now);
        }

        Map<String, Double> snap = d.tracker.snapshot(now, decay);
        Decision dec = ActionDecider.decide(snap, thresholds);

        if (vl >= thresholds.alertVl() && now - d.alertCooldown.getOrDefault(name, 0L) >= alertCooldownMs) {
            d.alertCooldown.put(name, now);
            alert(p, name, vl, evidence);
        }
        if (dec.level() == Level.PUNISH) {
            plugin.punishments().trigger(p, dec, summary(d, now));
        }
    }

    private void alert(Player p, String check, double vl, String evidence) {
        String line = plugin.msg("alert", "{oyuncu}", p.getName(), "{hile}", check, "{vl}", fmt(vl),
                "{ping}", String.valueOf(ServerUtil.ping(p)), "{kanit}", evidence == null ? "-" : evidence);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("voidguard.alerts") && !alertsOff.contains(staff.getUniqueId())) staff.sendMessage(line);
        }
        if (consoleAlerts) plugin.getLogger().info(org.bukkit.ChatColor.stripColor(line));
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
