package com.voidguard.anticheat.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class VgCommand implements CommandExecutor, TabCompleter {
    private final VoidGuard plugin;

    VgCommand(VoidGuard plugin) { this.plugin = plugin; }

    private void msg(CommandSender s, String m) { s.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4[&cVoidGuard&4] &7" + m)); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "alerts": {
                if (!(s instanceof Player)) { msg(s, "Yalnızca oyuncular."); return true; }
                Player p = (Player) s;
                msg(s, plugin.toggleAlerts(p.getUniqueId()) ? "Uyarılar &aAÇIK" : "Uyarılar &cKAPALI");
                break;
            }
            case "status": {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player ? (Player) s : null);
                PlayerData d = t == null ? null : plugin.peek(t.getUniqueId());
                if (d == null) { msg(s, "Oyuncu bulunamadı."); return true; }
                long now = System.currentTimeMillis();
                msg(s, String.format(Locale.ROOT, "&f%s &7toplam puan=&e%.1f &7ping=&e%d &7tps=&e%.1f",
                        t.getName(), d.suspicion.total(now), Compat.ping(t), plugin.tps()));
                Map<String, Double> snap = d.suspicion.snapshot(now);
                msg(s, snap.isEmpty() ? "Şüphe puanı yok." : "Puanlar: " + snap);
                StringBuilder vs = new StringBuilder();
                for (String ck : plugin.checkNames()) {
                    com.voidguard.anticheat.core.SanctionPolicy.Rule rule = plugin.cfg().rule(ck);
                    int n = plugin.ledger().count(t.getUniqueId(), ck, now, rule.expireMs());
                    if (n > 0) vs.append(ck).append('=').append(n).append(rule.banEligible() ? "/" + rule.violationsToBan() : "").append(' ');
                }
                msg(s, vs.length() == 0 ? "Kalıcı ihlal yok." : "İhlaller: &e" + vs);
                msg(s, "Aktif müdahale: &e" + (d.mitUntil.entrySet().stream().filter(x -> x.getValue() > now).map(x -> x.getKey() + " " + (x.getValue() - now) / 1000 + "sn").collect(java.util.stream.Collectors.toList()))
                        + " &7| Ban sistemi: " + (plugin.cfg().banActive() ? "&cAÇIK" : "&aKAPALI"));
                break;
            }
            case "history": msg(s, "Kayıtlar: plugins/VoidGuard/logs/vg-<tarih>.jsonl (alanlar: check, detail, ping, tps, history)"); break;
            case "exempt": {
                if (a.length < 2) { msg(s, "/vg exempt <oyuncu> [dakika]"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                PlayerData d = t == null ? null : plugin.peek(t.getUniqueId());
                if (d == null) { msg(s, "Oyuncu bulunamadı."); return true; }
                int min = 10;
                if (a.length > 2) { try { min = Math.max(1, Math.min(1440, Integer.parseInt(a[2]))); } catch (NumberFormatException ignored) { } }
                d.exemptUntilMs = VoidGuard.nowMs() + min * 60_000L;
                msg(s, t.getName() + " " + min + " dk muaf.");
                break;
            }
            case "reset": {
                if (a.length < 2) { msg(s, "/vg reset <oyuncu>"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                PlayerData d = t == null ? null : plugin.peek(t.getUniqueId());
                if (d == null) { msg(s, "Oyuncu bulunamadı."); return true; }
                d.suspicion.clear();
                d.mitUntil.clear();
                plugin.ledger().clear(t.getUniqueId());
                plugin.markDirty();
                d.build(plugin.cfg());
                msg(s, t.getName() + " için puanlar, kalıcı ihlaller ve kısıtlamalar sıfırlandı (yanlış pozitif düzeltme).");
                break;
            }
            case "unban": {
                if (a.length < 2) { msg(s, "/vg unban <oyuncu>"); return true; }
                java.util.UUID id = plugin.ledger().uuidByName(a[1]);
                if (id == null) { msg(s, "Bu isimde kayıtlı oyuncu yok."); return true; }
                plugin.ledger().unban(id);
                plugin.ledger().clear(id);
                plugin.markDirty();
                msg(s, a[1] + " banı kaldırıldı ve ihlalleri sıfırlandı.");
                break;
            }
            case "reload": { plugin.reloadAll(); msg(s, "Yapılandırma yeniden yüklendi."); break; }
            default: {
                msg(s, "&f/vg alerts &7- uyarıları aç/kapat");
                msg(s, "&f/vg status [oyuncu] &7- şüphe puanları");
                msg(s, "&f/vg exempt <oyuncu> [dk] &7- geçici muafiyet");
                msg(s, "&f/vg reset <oyuncu> &7- puanları sıfırla");
                msg(s, "&f/vg unban <oyuncu> &7- banı kaldır");
                msg(s, "&f/vg reload &7- config yenile   &f/vg history &7- log konumu");
                break;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : java.util.Arrays.asList("alerts", "status", "history", "exempt", "reset", "unban", "reload"))
                if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (a.length == 2 && java.util.Arrays.asList("status", "exempt", "reset").contains(a[0].toLowerCase(Locale.ROOT))) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
        }
        return out;
    }
}
