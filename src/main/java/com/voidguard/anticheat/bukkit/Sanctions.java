package com.voidguard.anticheat.bukkit;

import com.voidguard.anticheat.core.SanctionPolicy;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Doğrulanmış ihlallerde hile türüne özel geçici müdahale, oyuncu mesajı ve (kilitli) ban. */
final class Sanctions {
    private final VoidGuard plugin;

    Sanctions(VoidGuard plugin) { this.plugin = plugin; }

    private static String noun(String type) {
        switch (type) {
            case "ATTACK": return "saldırıların";
            case "MOVE": return "hareketlerin";
            case "PLACE": return "blok yerleştirmelerin";
            case "CONTAINER": return "konteyner işlemlerin";
            default: return "işlemlerin";
        }
    }

    private static String cap(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private void tell(Player p, String legacy) { p.sendMessage(legacy); }

    void execute(Player p, PlayerData d, String check, SanctionPolicy.Stage st, int n,
                 SanctionPolicy.Rule rule, long nowWall) {
        VoidGuardConfig c = plugin.cfg();
        String type = c.mitigationType(check);
        boolean canMitigate = c.b("sanctions.mitigation-enabled", true) && !type.equals("NONE");
        String counter = rule.banEligible()
                ? "§7(" + Math.min(n, rule.violationsToBan()) + "/" + rule.violationsToBan() + ")"
                : "§7(" + n + ")";
        switch (st) {
            case LOG:
                return;
            case WARN_MITIGATE:
                if (!canMitigate) return;
                start(p, d, type, rule.mitigationSeconds(), nowWall);
                tell(p, "§c§lVoidGuard §8» §eŞüpheli hareketlerin algılandı! " + rule.mitigationSeconds()
                        + " saniye boyunca " + noun(type) + " engellendi. " + counter);
                return;
            case RESTRICT:
                if (!canMitigate) return;
                start(p, d, type, rule.restrictSeconds(), nowWall);
                tell(p, "§c§lVoidGuard §8» §6Hile şüphesi devam ediyor! " + cap(noun(type)) + " kısıtlandı. " + counter);
                return;
            case BAN:
                if (c.banActive()) {
                    long until = nowWall + (long) (c.d("sanctions.ban-duration-days", 7) * 86_400_000L);
                    // Ban önce deftere yazılır: sahne sürerken çıkıp girerek kaçılamaz.
                    plugin.ledger().setBan(p.getUniqueId(), p.getName(), until, check);
                    plugin.saveLedger(false);
                    plugin.flagger().recordEvent("BAN", p, d, check, "n=" + n + " until=" + until, nowWall);
                    d.suspicion.clear();
                    int need = rule.violationsToBan();
                    if (c.b("sanctions.void-sequence.enabled", true)) plugin.voidSequence().start(p, d, until, need);
                    else kickBanned(p, until, need);
                } else {
                    plugin.flagger().recordEvent("WOULD_BAN", p, d, check, "n=" + n, nowWall);
                    if (canMitigate) {
                        start(p, d, type, rule.restrictSeconds(), nowWall);
                        tell(p, "§c§lVoidGuard §8» §6Hile şüphesi devam ediyor! " + cap(noun(type)) + " kısıtlandı. " + counter);
                    }
                    notifyWouldBan(p, d, check, n, nowWall);
                }
                return;
            default:
                return;
        }
    }

    /** Ban ekranıyla oyuncuyu atar (sahne bitince veya sahne kapalıysa hemen). */
    void kickBanned(Player p, long until, int need) {
        String counter = "§7(" + need + "/" + need + ")";
        p.kickPlayer("§c§lVoidGuard §8» §cBirden fazla hile ihlali tespit edildi. Sunucudan yasaklandın. "
                + counter + "\n" + remaining(until, System.currentTimeMillis()));
    }

    /** Giriş engeli ve kick mesajı için kalan süre. */
    static String remaining(long untilMs, long nowMs) {
        long m = Math.max(0, (untilMs - nowMs) / 60_000L);
        return "§7Kalan süre: §f" + (m / 1440) + " gün " + ((m % 1440) / 60) + " saat " + (m % 60) + " dk";
    }

    static String banScreen(long untilMs, long nowMs) {
        return "§c§lVoidGuard §8» §cHile kullanımı nedeniyle sunucudan yasaklandın.\n" + remaining(untilMs, nowMs);
    }

    private void notifyWouldBan(Player p, PlayerData d, String check, int n, long now) {
        VoidGuardConfig c = plugin.cfg();
        if (!c.b("alerts.notify-when-not-punished", true)) return;
        String msg = String.format(Locale.ROOT,
                "§4[§cVoidGuard§4] §c⚠ §c%s §7için ban koşulları oluştu (§c%s§7, %d. ihlal) ama ban KAPALI olduğu için §aatılmadı§7. §8/vg status %s",
                p.getName(), check, n, p.getName());
        for (Player s : Bukkit.getOnlinePlayers()) {
            if (!plugin.alertsEnabled(s.getUniqueId())) continue;
            if ((c.b("alerts.notify-ops", true) && s.isOp()) || s.hasPermission("voidguard.alerts")) s.sendMessage(msg);
        }
    }

    void start(Player p, PlayerData d, String type, int seconds, long nowWall) {
        long until = nowWall + seconds * 1000L;
        d.mitUntil.merge(type, until, Math::max);
        plugin.ledger().setMitigation(p.getUniqueId(), type, d.mitUntil.get(type));
        plugin.markDirty();
        if (type.equals("MOVE")) setback(p, d);
    }

    /** Oyuncuyu son temiz (yerde, kanıtsız) konuma geri alır. Bir sonraki tikte, tek görevle. */
    void setback(Player p, PlayerData d) {
        long now = System.currentTimeMillis();
        if (d.setbackQueued || !d.hasGood || now - d.lastSetbackMs < 250) return;
        d.setbackQueued = true;
        d.lastSetbackMs = now;
        Bukkit.getScheduler().runTask(plugin, () -> {
            d.setbackQueued = false;
            if (!p.isOnline() || d.goodWorld == null || !d.goodWorld.equals(p.getWorld())) return;
            Location to = new Location(d.goodWorld, d.goodX, d.goodY, d.goodZ, d.goodYaw, d.goodPitch);
            d.selfSetback = true;
            p.setVelocity(new Vector(0, 0, 0));
            p.setFallDistance(0f);
            p.teleport(to);
        });
    }
}
