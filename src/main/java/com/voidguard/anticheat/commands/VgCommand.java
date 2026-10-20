package com.voidguard.anticheat.commands;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.checks.Check;
import com.voidguard.anticheat.data.PlayerData;
import com.voidguard.anticheat.punishments.BanRecord;
import com.voidguard.anticheat.punishments.ViolationManager;
import com.voidguard.anticheat.storage.Database.ViolationRow;
import com.voidguard.anticheat.util.Compat;
import com.voidguard.anticheat.util.ServerUtil;
import com.voidguard.anticheat.util.TimeUtil;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /vg komutu (ve /voidguard). Her alt komutun ayrı izni vardır. */
public final class VgCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = Arrays.asList("help", "check", "alerts", "info", "unban", "reload", "violations", "test");
    private static final String PERM_PREFIX = "voidguard.";
    private final VoidGuard plugin;

    public VgCommand(VoidGuard plugin) {
        this.plugin = plugin;
    }

    private boolean allowed(CommandSender s, String sub) {
        String perm = sub.equals("help") ? "command" : sub;
        return s.hasPermission(PERM_PREFIX + perm) || s.hasPermission("voidguard.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!SUBS.contains(sub)) {
            plugin.send(sender, "unknown-command");
            return true;
        }
        if (!allowed(sender, sub)) {
            plugin.send(sender, "no-permission");
            return true;
        }
        switch (sub) {
            case "help": help(sender); break;
            case "check": check(sender, args); break;
            case "alerts": alerts(sender); break;
            case "info": info(sender, args); break;
            case "unban": unban(sender, args); break;
            case "reload": reload(sender); break;
            case "violations": violations(sender, args); break;
            case "test": test(sender); break;
            default: break;
        }
        return true;
    }

    private void help(CommandSender s) {
        for (String line : plugin.getConfig().getStringList("messages.help")) s.sendMessage(plugin.color(line));
    }

    private void check(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage(plugin.msg("usage", "{kullanim}", "/vg check <oyuncu>")); return; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { plugin.send(s, "player-not-found", "{oyuncu}", a[1]); return; }
        PlayerData d = plugin.data().get(t);
        long now = System.currentTimeMillis();
        s.sendMessage(plugin.msg("check-header", "{oyuncu}", t.getName(), "{ping}", String.valueOf(ServerUtil.ping(t))));
        s.sendMessage(plugin.color("&7Muaf: &f" + (plugin.exemptions().computeExempt(t) ? "Evet" : "Hayır")
                + " &8| &7Mod: &f" + t.getGameMode() + " &8| &7TPS: &f" + ViolationManager.fmt(ServerUtil.tps())));
        Map<String, Double> snap = d.tracker.snapshot(now, plugin.violations().decayPerMinute());
        if (snap.isEmpty()) {
            s.sendMessage(plugin.msgRaw("check-none"));
            return;
        }
        for (Map.Entry<String, Double> e : snap.entrySet()) {
            s.sendMessage(plugin.color("&8- &e" + e.getKey() + " &7VL: &c" + ViolationManager.fmt(e.getValue())));
        }
    }

    private void alerts(CommandSender s) {
        if (!(s instanceof Player)) { s.sendMessage("Bu komut yalnızca oyuncular içindir."); return; }
        boolean on = plugin.violations().toggleAlerts(((Player) s).getUniqueId());
        plugin.send(s, on ? "alerts-on" : "alerts-off");
    }

    /** Çevrimiçi, banlı veya önbellekteki isimden UUID bulur. */
    private UUID resolve(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p.getUniqueId();
        BanRecord b = plugin.punishments().findActiveByName(name);
        if (b != null) return b.uuid;
        OfflinePlayer op = Compat.offlineCached(name);
        return op == null ? null : op.getUniqueId();
    }

    private void info(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage(plugin.msg("usage", "{kullanim}", "/vg info <oyuncu>")); return; }
        UUID id = resolve(a[1]);
        if (id == null) { plugin.send(s, "player-not-found", "{oyuncu}", a[1]); return; }
        Player online = Bukkit.getPlayer(id);
        BanRecord ban = plugin.punishments().getActiveBan(id);
        s.sendMessage(plugin.msg("info-header", "{oyuncu}", a[1]));
        s.sendMessage(plugin.color("&7UUID: &f" + id));
        s.sendMessage(plugin.color("&7Durum: &f" + (online != null ? "Çevrimiçi, ping " + ServerUtil.ping(online) + " ms" : "Çevrimdışı")));
        s.sendMessage(plugin.color("&7Aktif ban: &f" + (ban == null ? "Yok" : ban.type + " | " + ban.checkName + " | "
                + (ban.expiresAt <= 0 ? "Kalıcı" : TimeUtil.format(ban.expiresAt - System.currentTimeMillis()) + " kaldı"))));
        plugin.db().countViolations(id).thenCombine(plugin.db().banHistory(id, 3), (cnt, hist) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                s.sendMessage(plugin.color("&7Kayıtlı ihlal sayısı: &f" + cnt));
                SimpleDateFormat f = new SimpleDateFormat("dd.MM.yyyy HH:mm");
                for (BanRecord h : hist) {
                    s.sendMessage(plugin.color("&8- &7" + f.format(new Date(h.bannedAt)) + " &f" + h.type + " &7(" + h.checkName + ")"
                            + (h.active ? " &c[AKTİF]" : "")));
                }
            });
            return null;
        });
    }

    private void unban(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage(plugin.msg("usage", "{kullanim}", "/vg unban <oyuncu>")); return; }
        BanRecord b = plugin.punishments().unban(a[1], s.getName());
        if (b == null) plugin.send(s, "not-banned", "{oyuncu}", a[1]);
        else plugin.send(s, "unbanned", "{oyuncu}", b.name);
    }

    private void reload(CommandSender s) {
        try {
            plugin.reloadAll();
            plugin.send(s, "reloaded");
        } catch (Throwable t) {
            s.sendMessage(plugin.color("&cYeniden yükleme hatası: " + t.getMessage()));
            plugin.getLogger().log(java.util.logging.Level.WARNING, "reload hatası", t);
        }
    }

    private void violations(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage(plugin.msg("usage", "{kullanim}", "/vg violations <oyuncu>")); return; }
        UUID id = resolve(a[1]);
        if (id == null) { plugin.send(s, "player-not-found", "{oyuncu}", a[1]); return; }
        plugin.db().recentViolations(id, 10).thenAccept(rows ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    s.sendMessage(plugin.msg("violations-header", "{oyuncu}", a[1]));
                    if (rows.isEmpty()) { s.sendMessage(plugin.msgRaw("check-none")); return; }
                    SimpleDateFormat f = new SimpleDateFormat("dd.MM HH:mm:ss");
                    for (ViolationRow r : rows) {
                        s.sendMessage(plugin.color("&8" + f.format(new Date(r.time())) + " &e" + r.check() + " &7VL "
                                + ViolationManager.fmt(r.vl()) + " &8" + r.evidence()));
                    }
                }));
    }

    /** Tanılama + efekt önizlemesi (yalnızca komutu çalıştıran yetkili üzerinde, ceza uygulanmaz). */
    private void test(CommandSender s) {
        List<String> on = new ArrayList<>();
        for (Check c : plugin.checks().all()) if (c.isEnabled()) on.add(c.getName());
        s.sendMessage(plugin.msg("test-header"));
        s.sendMessage(plugin.color("&7Etkin kontroller (&f" + on.size() + "&7): &f" + String.join(", ", on)));
        s.sendMessage(plugin.color("&7TPS: &f" + ViolationManager.fmt(ServerUtil.tps()) + " &8| &7Aktif ban: &f"
                + plugin.punishments().activeBanCount() + " &8| &7Ceza türü: &f" + plugin.getConfig().getString("punishment.type")));
        if (s instanceof Player) {
            Player p = (Player) s;
            if (!plugin.effect().isEnabled()) { plugin.send(s, "test-effect-disabled"); return; }
            if (plugin.effect().play(p, true, null)) plugin.send(s, "test-effect-started");
            else plugin.send(s, "test-effect-failed");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBS) if (sub.startsWith(args[0].toLowerCase(Locale.ROOT)) && allowed(sender, sub)) out.add(sub);
        } else if (args.length == 2 && Arrays.asList("check", "info", "violations", "unban").contains(args[0].toLowerCase(Locale.ROOT))
                && allowed(sender, args[0].toLowerCase(Locale.ROOT))) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
        }
        return out;
    }
}
