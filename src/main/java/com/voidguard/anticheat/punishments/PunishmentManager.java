package com.voidguard.anticheat.punishments;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.PlayerData;
import com.voidguard.anticheat.punishments.ActionDecider.Decision;
import com.voidguard.anticheat.util.TimeUtil;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** Ceza uygulama, ban önbelleği (bellekte) ve kalıcı kayıt (SQLite). */
public final class PunishmentManager {
    private final VoidGuard plugin;
    private final Map<UUID, BanRecord> activeBans = new ConcurrentHashMap<>();
    private final Set<UUID> punishing = ConcurrentHashMap.newKeySet();

    public PunishmentManager(VoidGuard plugin) {
        this.plugin = plugin;
    }

    /** Sunucu yeniden başlatıldığında kalıcı banlar veritabanından geri yüklenir. */
    public void load() {
        activeBans.clear();
        for (BanRecord b : plugin.db().loadActiveBans()) activeBans.put(b.uuid, b);
        plugin.getLogger().info(activeBans.size() + " aktif ban yüklendi.");
    }

    public boolean isPunishing(UUID id) {
        return punishing.contains(id);
    }

    public int activeBanCount() {
        return activeBans.size();
    }

    /** Doğrulanmış güçlü kanıt: önce (varsa) görsel efekt, sonra ceza. */
    public void trigger(Player p, Decision dec, String summary) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("punishment.enabled", true)) return;
        UUID id = p.getUniqueId();
        PlayerData d = plugin.data().get(p);
        String check = dec.topCheck() == null ? "Bilinmiyor" : dec.topCheck();

        if (c.getBoolean("punishment.dry-run", false)) {
            plugin.getLogger().info("[DRY-RUN] " + p.getName() + " cezalandırılacaktı: " + check + " | " + summary);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("voidguard.alerts")) {
                    staff.sendMessage(plugin.msg("dry-run", "{oyuncu}", p.getName(), "{hile}", check));
                }
            }
            d.tracker.clear();
            return;
        }
        if (!punishing.add(id)) return;

        final String name = p.getName();
        Runnable apply = () -> apply(id, name, check, summary);
        boolean played = false;
        if (dec.highConfidence() && p.isOnline() && plugin.effect().isEnabled()) {
            played = plugin.effect().play(p, false, apply);
        }
        if (!played) apply.run();
    }

    @SuppressWarnings("deprecation")
    private void apply(UUID id, String name, String check, String summary) {
        try {
            FileConfiguration c = plugin.getConfig();
            String type = c.getString("punishment.type", "TEMPBAN").toUpperCase(Locale.ROOT);
            String reason = c.getString("punishment.reason", "VOIDGUARD: Doğrulanmış hile tespiti ({hile})")
                    .replace("{hile}", check);
            long now = System.currentTimeMillis();
            BanRecord rec;
            if (type.equals("KICK")) {
                rec = new BanRecord(0, id, name, reason, check, summary, "KICK", "VOIDGUARD", now, now, false);
                plugin.db().insertBan(rec);
            } else {
                long dur = type.equals("BAN") ? 0 : TimeUtil.parseDuration(c.getString("punishment.duration", "1d"));
                if (dur < 0) dur = TimeUtil.parseDuration("1d");
                rec = ban(id, name, reason, check, summary, dur, "VOIDGUARD");
            }
            PlayerData d = plugin.data().getIfPresent(id);
            if (d != null) d.tracker.clear();

            Player pl = Bukkit.getPlayer(id);
            if (pl != null && pl.isOnline()) pl.kickPlayer(banScreen(rec));

            String line = plugin.msg("punished", "{oyuncu}", name, "{hile}", check, "{tur}", rec.type);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("voidguard.alerts")) staff.sendMessage(line);
            }
            plugin.getLogger().info("CEZA: " + name + " (" + id + ") " + rec.type + " | " + check + " | " + summary);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Ceza uygulanırken hata", t);
        } finally {
            punishing.remove(id);
        }
    }

    public BanRecord ban(UUID id, String name, String reason, String check, String evidence, long durationMs, String by) {
        long now = System.currentTimeMillis();
        boolean perm = durationMs <= 0;
        BanRecord rec = new BanRecord(0, id, name, reason, check, evidence, perm ? "BAN" : "TEMPBAN", by, now,
                perm ? 0 : now + durationMs, true);
        activeBans.put(id, rec);
        plugin.db().insertBan(rec).thenAccept(newId -> rec.id = newId);
        return rec;
    }

    /** Aktif ban kaydı; süresi dolmuşsa otomatik kaldırılır. Async iş parçacığından güvenle çağrılabilir. */
    public BanRecord getActiveBan(UUID id) {
        BanRecord b = activeBans.get(id);
        if (b == null) return null;
        if (b.expired(System.currentTimeMillis())) {
            activeBans.remove(id);
            plugin.db().deactivate(id, "SÜRE DOLDU", System.currentTimeMillis());
            return null;
        }
        return b;
    }

    public BanRecord findActiveByName(String name) {
        for (BanRecord b : activeBans.values()) {
            if (b.name != null && b.name.equalsIgnoreCase(name)) return b;
        }
        return null;
    }

    public BanRecord unban(String name, String by) {
        BanRecord b = findActiveByName(name);
        if (b == null) return null;
        activeBans.remove(b.uuid);
        b.active = false;
        plugin.db().deactivate(b.uuid, by, System.currentTimeMillis());
        return b;
    }

    public String banScreen(BanRecord b) {
        String date = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(b.bannedAt));
        String dur = b.type.equals("KICK") ? "-" : (b.expiresAt <= 0 ? "Kalıcı"
                : TimeUtil.format(Math.max(0, b.expiresAt - System.currentTimeMillis())) + " kaldı");
        String key = b.type.equals("KICK") ? "kick-screen" : "ban-screen";
        String text = String.join("\n", plugin.getConfig().getStringList("messages." + key));
        text = text.replace("{sunucu}", plugin.getConfig().getString("server-name", "Sunucu"))
                .replace("{hile}", b.checkName == null ? "-" : b.checkName)
                .replace("{sebep}", b.reason == null ? "-" : b.reason)
                .replace("{sure}", dur).replace("{tarih}", date).replace("{id}", String.valueOf(b.id));
        return plugin.color(text);
    }
}
