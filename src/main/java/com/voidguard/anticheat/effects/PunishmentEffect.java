package com.voidguard.anticheat.effects;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.util.Compat;
import com.voidguard.anticheat.util.ServerUtil;
import com.voidguard.anticheat.util.WorldUtil;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * VOIDGUARD görsel ceza efekti. YALNIZCA parçacık ve ses kullanır:
 * gerçek patlama yok, blok hasarı yok, envantere dokunulmaz (sahte eşya = yalnızca parçacık).
 * Sabitleme ve (isteğe bağlı) yükselme her koşulda animasyon sonunda güvenle geri alınır.
 */
public final class PunishmentEffect {
    private static final class Session {
        BukkitTask task;
        Runnable onFinish;
        Location origin;
        boolean freeze;
        double liftMax;
        boolean liftApplied;
    }

    private final VoidGuard plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Particle dust = resolve("DUST", "REDSTONE");
    private final Particle explosion = resolve("EXPLOSION_LARGE", "EXPLOSION");
    private final Particle emitter = resolve("EXPLOSION_HUGE", "EXPLOSION_EMITTER");
    private final Particle itemParticle = resolve("ITEM_CRACK", "ITEM");
    private final Particle smoke = resolve("SMOKE_LARGE", "LARGE_SMOKE");

    public PunishmentEffect(VoidGuard plugin) {
        this.plugin = plugin;
    }

    private static Particle resolve(String... names) {
        for (String n : names) {
            try {
                return Particle.valueOf(n);
            } catch (Throwable ignored) {
                // sonraki sürüm adını dene (yeni API'de farklı/olmayan tür olabilir)
            }
        }
        return null;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("effect.enabled", true) && dust != null;
    }

    public boolean isActive(UUID id) {
        return sessions.containsKey(id);
    }

    public boolean isFrozen(UUID id) {
        Session s = sessions.get(id);
        return s != null && s.freeze;
    }

    /** Efekti başlatır. Başlatılamazsa false döner (çağıran cezayı doğrudan uygular). */
    public boolean play(Player p, boolean preview, Runnable onFinish) {
        FileConfiguration c = plugin.getConfig();
        if (!isEnabled() || p == null || !p.isOnline() || p.isDead()) return false;
        UUID id = p.getUniqueId();
        if (sessions.containsKey(id) || sessions.size() >= Math.max(1, c.getInt("effect.max-concurrent", 3))) return false;
        if (ServerUtil.tps() < c.getDouble("effect.min-tps", 17.0)) return false; // sunucu zaten yavaşsa animasyon yok

        final Session s = new Session();
        s.onFinish = onFinish;
        s.origin = p.getLocation().clone();
        s.freeze = c.getBoolean("effect.freeze.enabled", true);
        if (c.getBoolean("effect.lift.enabled", false)) {
            s.liftMax = safeLift(s.origin, Math.min(5.0, c.getDouble("effect.lift.height", 2.0)));
        }
        sessions.put(id, s);
        try {
            return start(p, s, id, c);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Efekt başlatılamadı, ceza doğrudan uygulanacak", t);
            finishQuietly(id);
            return false;
        }
    }

    private boolean start(Player p, Session s, UUID id, FileConfiguration c) {
        if (c.getBoolean("effect.explosion.fuse-sound", true) && c.getBoolean("effect.explosion.sound", true)) {
            p.getWorld().playSound(p.getLocation(), "entity.tnt.primed", 1.0f, 1.0f);
        }

        final int interval = Math.max(1, Math.min(5, c.getInt("effect.tick-interval", 2)));
        final int seconds = Math.max(1, Math.min(10, c.getInt("effect.duration-seconds", 4)));
        final int total = Math.max(1, (seconds * 20) / interval);

        s.task = new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                Player pl = Bukkit.getPlayer(id);
                if (pl == null || !pl.isOnline() || pl.isDead() || !pl.getWorld().equals(s.origin.getWorld())) {
                    finish(id); // çıkış / ölüm / dünya değişimi: görev iptal, ceza gecikmeden uygulanır
                    return;
                }
                try {
                    tick(pl, s, step, total);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Efekt hatası, ceza doğrudan uygulanıyor", t);
                    finish(id);
                    return;
                }
                step++;
                if (step >= total) {
                    try {
                        explode(pl);
                    } catch (Throwable t) {
                        plugin.getLogger().log(Level.WARNING, "Patlama efekti hatası", t);
                    }
                    finish(id);
                }
            }
        }.runTaskTimer(plugin, 1L, interval);
        return true;
    }

    /** Oturumu sonlandırır: sabitleme kalkar, konum geri yüklenir, sonra (varsa) ceza çalışır. */
    public void finish(UUID id) {
        Session s = sessions.remove(id);
        if (s == null) return;
        try {
            if (s.task != null) s.task.cancel();
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && s.liftApplied && s.origin.getWorld() != null) {
                p.teleport(s.origin, PlayerTeleportEvent.TeleportCause.PLUGIN);
                p.setFallDistance(0f);
                p.setVelocity(new Vector(0, 0, 0));
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Efekt temizliği hatası (ceza yine uygulanacak)", t);
        }
        if (s.onFinish != null) {
            try {
                s.onFinish.run();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Efekt sonrası ceza hatası", t);
            }
        }
    }

    /** Oturumu ceza çalıştırmadan kapatır (çağıran cezayı kendisi uygular). */
    public void finishQuietly(UUID id) {
        Session s = sessions.remove(id);
        if (s == null) return;
        try {
            if (s.task != null) s.task.cancel();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Efekt görevi iptal edilemedi", t);
        }
    }

    public void onQuit(UUID id) {
        finish(id); // oyuncu çıkarsa ceza yine de uygulanır (ban kaçışını önler)
    }

    public void shutdown() {
        for (UUID id : sessions.keySet()) finish(id);
    }

    private double safeLift(Location o, double want) {
        World w = o.getWorld();
        double ok = 0;
        for (int h = 1; h <= (int) Math.floor(want); h++) {
            Block a = WorldUtil.block(w, o.getX(), o.getY() + h, o.getZ());
            Block b = WorldUtil.block(w, o.getX(), o.getY() + h + 1, o.getZ());
            if (a == null || b == null || !a.isPassable() || !b.isPassable() || a.isLiquid() || b.isLiquid()) break;
            ok = h;
        }
        return ok;
    }

    private void tick(Player p, Session s, int step, int total) {
        FileConfiguration c = plugin.getConfig();
        double progress = (step + 1.0) / total;

        if (s.liftMax > 0) {
            Location cur = p.getLocation();
            Location t = s.origin.clone().add(0, s.liftMax * Math.sin(progress * Math.PI / 2), 0);
            t.setYaw(cur.getYaw());
            t.setPitch(cur.getPitch());
            p.teleport(t, PlayerTeleportEvent.TeleportCause.PLUGIN);
            s.liftApplied = true;
        }

        Location center = p.getLocation();
        World w = center.getWorld();
        if (w == null) return;

        double minR = c.getDouble("effect.ring.min-radius", 0.8);
        double maxR = c.getDouble("effect.ring.max-radius", 3.0);
        double cycles = Math.max(0.5, c.getDouble("effect.ring.cycles", 2.0));
        double wave = c.getDouble("effect.ring.height-wave", 0.25);
        double r = minR + (maxR - minR) * (0.5 - 0.5 * Math.cos(2 * Math.PI * cycles * progress)); // genişle-daral

        int budget = Math.max(8, c.getInt("effect.max-particles-per-tick", 80));
        int n = Math.max(8, Math.min(Math.min(64, budget), c.getInt("effect.ring.particles", 28)));
        if (ServerUtil.tps() < c.getDouble("effect.min-tps", 17.0) + 1.0) n = Math.max(8, n / 2);

        float size = (float) c.getDouble("effect.ring.size", 1.4);
        Particle.DustOptions red = new Particle.DustOptions(color(c.getString("effect.ring.primary-color"), Color.fromRGB(220, 20, 40)), size);
        Particle.DustOptions purple = new Particle.DustOptions(color(c.getString("effect.ring.secondary-color"), Color.fromRGB(140, 40, 200)), size);
        Particle ringType = ringParticle(c.getString("effect.ring.particle", "DUST"));
        boolean colored = ringType == dust;

        double spin = step * 0.35;
        for (int i = 0; i < n; i++) {
            double a = spin + (2 * Math.PI * i) / n;
            double x = center.getX() + r * Math.cos(a);
            double z = center.getZ() + r * Math.sin(a);
            double y = center.getY() + 0.15 + wave * Math.sin(a * 3 + spin);
            if (colored) {
                w.spawnParticle(dust, x, y, z, 1, 0, 0, 0, 0, (i % 2 == 0) ? red : purple);
            } else {
                w.spawnParticle(ringType, x, y, z, 1, 0, 0, 0, 0);
            }
        }

        boolean lowTps = ServerUtil.tps() < c.getDouble("effect.min-tps", 17.0) + 1.0;
        int left = budget - n;
        if (!lowTps && left >= 6 && colored) {
            if (c.getBoolean("effect.rise.enabled", true)) {
                riseColumn(w, center, step, progress, Math.min(10, left / 3), red, purple);
            }
            if (c.getBoolean("effect.wither-head.enabled", true)) {
                witherHead(w, center, step, progress, Math.min(6, Math.max(0, (left - Math.min(10, left / 3)) / 4)));
            }
        }

        if (c.getBoolean("effect.fake-items.enabled", false) && itemParticle != null && step % 2 == 0) {
            spawnFakeItems(p, w, center, Math.max(1, Math.min(8, c.getInt("effect.fake-items.count", 4))));
        }
    }

    /** Yukarı yükselme hissi: oyuncunun etrafında yukarı doğru tırmanan parçacık spirali (oyuncu gerçekte hareket etmez). */
    private void riseColumn(World w, Location center, int step, double progress, int count, Particle.DustOptions red, Particle.DustOptions purple) {
        double height = 0.3 + 2.4 * progress;
        for (int i = 0; i < count; i++) {
            double t = (double) i / Math.max(1, count);
            double a = step * 0.6 + t * 2 * Math.PI * 2;
            double y = center.getY() + (height * ((t + progress * 2) % 1.0));
            double rr = 0.6 + 0.2 * Math.sin(a * 2);
            w.spawnParticle(dust, center.getX() + rr * Math.cos(a), y, center.getZ() + rr * Math.sin(a), 1, 0, 0, 0, 0, (i % 2 == 0) ? purple : red);
        }
    }

    /** Wither kafası görünümü (yalnızca parçacık): oyuncunun üstünde üç koyu "kafa" ve kırmızı gözler. */
    private void witherHead(World w, Location center, int step, double progress, int perHead) {
        if (perHead <= 0) return;
        Particle.DustOptions dark = new Particle.DustOptions(Color.fromRGB(35, 35, 40), 1.2f);
        Particle.DustOptions eye = new Particle.DustOptions(Color.fromRGB(255, 30, 30), 0.9f);
        double baseY = center.getY() + 2.2 + 0.8 * progress;
        double spin = step * 0.15;
        double[] offs = {0.0, -0.75, 0.75};
        for (int h = 0; h < offs.length; h++) {
            double sc = h == 0 ? 1.0 : 0.75;
            double cx = center.getX() + offs[h] * Math.cos(spin);
            double cz = center.getZ() + offs[h] * Math.sin(spin);
            double cy = baseY - (h == 0 ? 0 : 0.2);
            for (int i = 0; i < perHead; i++) {
                double a = (2 * Math.PI * i) / perHead + spin;
                w.spawnParticle(dust, cx + 0.22 * sc * Math.cos(a), cy + 0.22 * sc * Math.sin(a * 2) , cz + 0.22 * sc * Math.sin(a), 1, 0, 0, 0, 0, dark);
            }
            w.spawnParticle(dust, cx - 0.08 * sc, cy + 0.05, cz, 1, 0, 0, 0, 0, eye);
            w.spawnParticle(dust, cx + 0.08 * sc, cy + 0.05, cz, 1, 0, 0, 0, 0, eye);
        }
    }

    /** Sahte eşya görseli: envanterden yalnızca malzeme türü okunur, kopya ItemStack parçacık verisi olur. */
    private void spawnFakeItems(Player p, World w, Location center, int count) {
        ItemStack[] contents = p.getInventory().getStorageContents();
        int spawned = 0;
        for (int i = 0; i < contents.length && spawned < count; i++) {
            ItemStack it = contents[i];
            if (it == null) continue;
            Material m = it.getType();
            if (Compat.isAir(m) || !m.isItem()) continue;
            w.spawnParticle(itemParticle, center.getX(), center.getY() + 1.0, center.getZ(), 3, 0.6, 0.5, 0.6, 0.08, new ItemStack(m));
            spawned++;
        }
    }

    private void explode(Player p) {
        FileConfiguration c = plugin.getConfig();
        Location loc = p.getLocation().add(0, 1.0, 0);
        World w = loc.getWorld();
        if (w == null) return;
        if (c.getBoolean("effect.explosion.particles", true)) {
            int cnt = Math.max(1, Math.min(6, c.getInt("effect.explosion.particle-count", 2)));
            if (explosion != null) w.spawnParticle(explosion, loc, cnt, 0.4, 0.4, 0.4, 0);
            if (emitter != null) w.spawnParticle(emitter, loc, 1, 0, 0, 0, 0);
            if (smoke != null) w.spawnParticle(smoke, loc, 8, 0.5, 0.5, 0.5, 0.02);
            float size = (float) c.getDouble("effect.ring.size", 1.4);
            Particle.DustOptions red = new Particle.DustOptions(color(c.getString("effect.ring.primary-color"), Color.fromRGB(220, 20, 40)), size);
            Particle.DustOptions purple = new Particle.DustOptions(color(c.getString("effect.ring.secondary-color"), Color.fromRGB(140, 40, 200)), size);
            int burst = Math.min(40, c.getInt("effect.max-particles-per-tick", 80) / 2);
            for (int i = 0; i < burst; i++) {
                double th = Math.random() * 2 * Math.PI;
                double ph = Math.acos(2 * Math.random() - 1);
                Vector v = new Vector(Math.sin(ph) * Math.cos(th), Math.cos(ph), Math.sin(ph) * Math.sin(th)).multiply(0.4 + Math.random() * 1.6);
                w.spawnParticle(dust, loc.clone().add(v), 1, 0, 0, 0, 0, (i % 2 == 0) ? red : purple);
            }
        }
        if (c.getBoolean("effect.explosion.sound", true)) {
            w.playSound(loc, "entity.generic.explode", (float) c.getDouble("effect.explosion.volume", 1.0),
                    (float) c.getDouble("effect.explosion.pitch", 1.0));
        }
        // GERÇEK patlama oluşturulmaz: createExplosion / TNT varlığı / blok hasarı kullanılmaz.
    }

    private Particle ringParticle(String name) {
        if (name == null) return dust;
        String n = name.trim().toUpperCase(java.util.Locale.ROOT);
        if (n.equals("DUST") || n.equals("REDSTONE")) return dust;
        Particle p = resolve(n);
        return p == null ? dust : p;
    }

    private static Color color(String rgb, Color def) {
        if (rgb == null) return def;
        try {
            String[] p = rgb.split(",");
            return Color.fromRGB(clamp(Integer.parseInt(p[0].trim())), clamp(Integer.parseInt(p[1].trim())), clamp(Integer.parseInt(p[2].trim())));
        } catch (Exception e) {
            return def;
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
