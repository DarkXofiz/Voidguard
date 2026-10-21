package com.voidguard.anticheat.bukkit;

import com.voidguard.anticheat.core.Result;
import com.voidguard.anticheat.core.SanctionPolicy;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Kanıt -> kontrol başına puan -> (doğrulanmışsa) kalıcı ihlal -> kademeli müdahale. */
final class Flagger {
    private final VoidGuard plugin;

    Flagger(VoidGuard plugin) { this.plugin = plugin; }

    void flag(Player p, PlayerData d, String check, Result r) {
        if (r == null || d.voidSeq) return;
        VoidGuardConfig c = plugin.cfg();
        if (!c.enabled(check)) return;
        double w = r.weight() * c.weight(check);
        if (w <= 0) return;

        long now = System.currentTimeMillis();
        double pts = d.suspicion.add(check, w, now);
        double total = d.suspicion.total(now);
        SanctionPolicy.Rule rule = c.rule(check);
        SanctionPolicy.Level lvl = SanctionPolicy.level(pts, rule);

        record("EVIDENCE", p, d, check, "[" + lvl + "] " + r.detail(), w, pts, total, now);
        alert(p, d, check, r.detail(), pts, now);
        if (d.voidSeq) return;                                  // 3. log: ban sahnesi başladı
        instant(p, d, check, pts, now);                        // log düştüğü anda hileye özel anında engel
        if (lvl == SanctionPolicy.Level.LOW) return;          // düşük güven: ihlal sayılmaz (yalnızca kayıt + kısa engel)

        // Aktif hareket kısıtı varken doğrulanmış yeni kanıt -> anında geri alma (ihlal sayısı artmaz)
        if (c.b("sanctions.mitigation-enabled", true) && c.mitigationType(check).equals("MOVE") && d.mitActive("MOVE", now))
            plugin.sanctions().setback(p, d);

        // Yeni "ihlal" yalnızca cooldown sonrası sayılır: her şüpheli hareket ban sebebi olmaz
        UUID id = p.getUniqueId();
        if (now - plugin.ledger().lastViolation(id, check) < rule.violationCooldownMs()) return;
        int n = plugin.ledger().record(id, p.getName(), check, now, rule.expireMs());
        int distinct = plugin.ledger().distinctChecks(id, now, k -> plugin.cfg().rule(k).expireMs());
        SanctionPolicy.Stage st = SanctionPolicy.stage(lvl, n, rule, distinct, c.i("sanctions.min-distinct-checks-for-ban", 2));
        plugin.markDirty();
        record("VIOLATION_" + st, p, d, check, "n=" + n + " bağımsız=" + distinct + " seviye=" + lvl + " | " + r.detail(), w, pts, total, now);
        plugin.sanctions().execute(p, d, check, st, n, rule, now);
    }

    /**
     * Kanıt kaydedildiği anda kısa süreli, kendiliğinden biten engel: saldırı/blok/konteyner iptali veya
     * hareket dondurma + son temiz konuma geri alma. İhlal sayısını ARTIRMAZ (3/3 sayacından bağımsız).
     */
    private void instant(Player p, PlayerData d, String check, double pts, long now) {
        VoidGuardConfig c = plugin.cfg();
        if (!c.b("sanctions.mitigation-enabled", true) || !c.b("sanctions.instant.enabled", true)) return;
        if (!c.cb(check, "instant", false)) return;
        if (pts < c.d("sanctions.instant.min-points", 2)) return;
        String type = c.mitigationType(check);
        switch (type) {
            case "ATTACK":
            case "PLACE":
            case "CONTAINER":
                d.mitUntil.merge(type, now + (long) (c.d("sanctions.instant.block-seconds", 3) * 1000), Math::max);
                break;
            case "MOVE":
                d.mitUntil.merge("MOVE", now + (long) (c.d("sanctions.instant.move-freeze-seconds", 2) * 1000), Math::max);
                plugin.sanctions().setback(p, d);
                break;
            default:
                break;
        }
    }

    void recordEvent(String type, Player p, PlayerData d, String check, String detail, long now) {
        record(type, p, d, check, detail, 0, d.suspicion.points(check, now), d.suspicion.total(now), now);
    }

    private static void prune(java.util.ArrayDeque<Long> q, long cutoff) {
        while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
    }

    /**
     * Staff logu. Yetkililere giden HER log sayılır (min-gap aralıklı, window içinde):
     *  - 2. logda saldırılar 30 sn engellenir,
     *  - ban-uygun hilelerin 3. logunda oyuncu donar, kararır, 5 blok yükselir ve banlanır.
     * Yalnızca ban-uygun kontroller ban sayacına girer; kanıt zaten tampon ve lag/muafiyet filtresinden geçmiştir.
     */
    private void alert(Player p, PlayerData d, String check, String detail, double pts, long now) {
        VoidGuardConfig c = plugin.cfg();
        boolean opsAll = c.b("alerts.notify-ops", true) && c.b("alerts.ops-see-all-evidence", true);
        double threshold = c.d("suspicion.alert-points-per-check", 4);
        boolean real = pts >= threshold;
        if (!real && !opsAll) return;
        Long last = d.lastAlertMs.get(check);
        if (last != null && now - last < c.i("suspicion.alert-cooldown-seconds", 3) * 1000L) return;
        d.lastAlertMs.put(check, now);

        boolean lb = c.b("sanctions.log-ban.enabled", true);
        int need = Math.max(1, c.i("sanctions.log-ban.logs-required", 3));
        double minPts = c.d("sanctions.log-ban.min-points", 1.0);
        boolean counted = false, eligible = c.rule(check).banEligible();
        if (lb && pts >= minPts && !d.voidSeq) {
            long window = c.i("sanctions.log-ban.window-seconds", 120) * 1000L;
            long gap = c.i("sanctions.log-ban.min-gap-seconds", 2) * 1000L;
            prune(d.logAll, now - window);
            prune(d.logBan, now - window);
            if (now - d.lastLogMs >= gap) {
                counted = true;
                d.lastLogMs = now;
                d.logAll.addLast(now);
                if (eligible) d.logBan.addLast(now);
            }
        }

        StringBuilder sb = new StringBuilder("&4&l⚠ &c&lVOIDGUARD &8» &f").append(p.getName())
                .append(" &8| &c").append(check)
                .append(String.format(Locale.ROOT, " &8(&c%.1f &7puan&8)", pts));
        if (counted && eligible) sb.append(" &8| &cLOG &4").append(Math.min(d.logBan.size(), need)).append("&8/&4").append(need);
        sb.append(" &8| &7").append(detail);
        sb.append(" &8| &7ping &f").append(Compat.ping(p));
        String msg = ChatColor.translateAlternateColorCodes('&', sb.toString());
        for (Player s : Bukkit.getOnlinePlayers()) {
            if (!plugin.alertsEnabled(s.getUniqueId())) continue;
            boolean op = c.b("alerts.notify-ops", true) && s.isOp();
            boolean staff = s.hasPermission("voidguard.alerts");
            if (op && opsAll) s.sendMessage(msg);
            else if ((staff || op) && (real || counted)) s.sendMessage(msg);
        }
        if (c.b("logging.console", false) && d.suspicion.total(now) >= c.d("logging.console-min-total", 8))
            plugin.getLogger().info(p.getName() + " " + check + " (" + String.format(Locale.ROOT, "%.1f", pts) + ") " + detail);

        if (!counted) return;
        // Çok log düştü: saldırılar belirli süre engellenir
        int blockAt = c.i("sanctions.log-ban.attack-block-at", 2);
        if (c.b("sanctions.mitigation-enabled", true) && d.logAll.size() >= blockAt) {
            long secs = c.i("sanctions.log-ban.attack-block-seconds", 30);
            boolean first = !d.mitActive("ATTACK", now);
            d.mitUntil.merge("ATTACK", now + secs * 1000L, Math::max);
            if (first) p.sendMessage("§c§lVoidGuard §8» §cŞüpheli davranışların yetkililere bildirildi. §7Vuruşların §c" + secs
                    + " saniye §7engellendi.");
        }
        // Ban sayacı doldu
        if (eligible && d.logBan.size() >= need) {
            d.logBan.clear();
            d.logAll.clear();
            plugin.sanctions().logBan(p, d, check, now);
        }
    }

    private void record(String type, Player p, PlayerData d, String check, String detail,
                        double weight, double pts, double total, long now) {
        if (!plugin.cfg().b("logging.file", true)) return;
        Location l = p.getLocation();
        Map<String, Double> snap = d.suspicion.snapshot(now);
        StringBuilder h = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> e : snap.entrySet()) {
            if (!first) h.append(',');
            first = false;
            h.append('"').append(ViolationLog.esc(e.getKey())).append("\":").append(e.getValue());
        }
        h.append('}');
        String json = String.format(Locale.ROOT,
                "{\"ts\":\"%s\",\"type\":\"%s\",\"player\":\"%s\",\"uuid\":\"%s\",\"check\":\"%s\",\"detail\":\"%s\","
                        + "\"weight\":%.2f,\"points\":%.2f,\"total\":%.2f,\"ping\":%d,\"tps\":%.2f,"
                        + "\"gamemode\":\"%s\",\"world\":\"%s\",\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"history\":%s}",
                java.time.Instant.ofEpochMilli(now), type, ViolationLog.esc(p.getName()), d.uuid, ViolationLog.esc(check),
                ViolationLog.esc(detail), weight, pts, total, Compat.ping(p), plugin.tps(), p.getGameMode(),
                ViolationLog.esc(l.getWorld().getName()), l.getX(), l.getY(), l.getZ(), h);
        plugin.violationLog().write(json);
    }
}
