package com.voidguard.anticheat.bukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Ban sahnesi: oyuncu olduğu yerde donar (hareket edemez), etrafında kırmızı partikül çemberi döner,
 * sonra yukarı doğru havalanır ve tepede ban ekranıyla sunucudan atılır.
 * Ban deftere sahne BAŞLAMADAN yazılmıştır; sahne sırasında çıkan oyuncu yine banlıdır.
 * Yalnızca 1.13 API'si + Compat kullanır (1.13 - 1.21.x).
 */
final class VoidSequence {
    private static final Particle DUST = Compat.particle("REDSTONE", "DUST");     // 1.20.5'te DUST oldu
    private static final Particle SMOKE = Compat.particle("LARGE_SMOKE", "SMOKE_LARGE");
    private static final Particle SPARK = Compat.particle("SOUL_FIRE_FLAME", "FLAME");
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(220, 0, 0), 1.5f);
    private static final PotionEffectType BLIND = Compat.effect("BLINDNESS");
    private static final PotionEffectType DARKNESS = Compat.effect("DARKNESS");   // 1.19+
    private static final Particle.DustOptions DARK_RED = new Particle.DustOptions(Color.fromRGB(120, 0, 0), 1.2f);

    private static final class Run {
        final Player player;
        final PlayerData data;
        final Location base;
        final long until;
        final int need, freeze, rise;
        final double height, radius;
        int darkTick, darkRadius;
        boolean darkened;
        final List<UUID> darkPlayers = new ArrayList<UUID>();
        final float walkSpeed;
        final boolean gravity, invulnerable;
        BukkitTask task;
        int t;

        Run(Player p, PlayerData d, long until, int need, int freeze, int rise, double height, double radius) {
            this.player = p; this.data = d; this.until = until; this.need = need;
            this.freeze = freeze; this.rise = rise; this.height = height; this.radius = radius;
            this.base = p.getLocation().clone();
            this.walkSpeed = p.getWalkSpeed();
            this.gravity = p.hasGravity();
            this.invulnerable = p.isInvulnerable();
        }
    }

    private final VoidGuard plugin;
    private final Map<UUID, Run> runs = new HashMap<>();   // yalnızca ana iş parçacığı

    VoidSequence(VoidGuard plugin) { this.plugin = plugin; }

    boolean active(UUID id) { return runs.containsKey(id); }

    void start(Player p, PlayerData d, long until, int need) {
        UUID id = p.getUniqueId();
        if (runs.containsKey(id)) return;
        VoidGuardConfig c = plugin.cfg();
        int freeze = Math.max(5, c.i("sanctions.void-sequence.freeze-ticks", 40));
        int rise = Math.max(10, c.i("sanctions.void-sequence.rise-ticks", 40));
        double height = Math.max(1.0, c.d("sanctions.void-sequence.rise-height", 5.0));
        double radius = Math.max(0.6, c.d("sanctions.void-sequence.ring-radius", 1.4));

        final Run r = new Run(p, d, until, need, freeze, rise, height, radius);
        r.darkTick = c.b("sanctions.void-sequence.darken", true) ? Math.max(0, c.i("sanctions.void-sequence.darken-tick", 12)) : -1;
        r.darkRadius = Math.max(0, c.i("sanctions.void-sequence.darken-radius", 20));
        runs.put(id, r);
        d.voidSeq = true;
        d.velocity.cancel();

        p.closeInventory();
        p.setWalkSpeed(0f);
        p.setGravity(false);
        p.setInvulnerable(true);
        p.setSprinting(false);
        p.setVelocity(new Vector(0, 0, 0));
        p.setFallDistance(0f);

        World w = p.getWorld();
        w.playSound(r.base, "block.beacon.activate", 1.2f, 0.7f);
        p.sendTitle("§4§l⚠ VOIDGUARD ⚠", "§cHile tespit edildi", 5, 70, 10);
        p.sendMessage("§c§lVoidGuard §8» §cHile kullanımı tespit edildi. Hareket edemezsin.");

        r.task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { tick(r); }
        }, 0L, 1L);
    }

    /** Yukarıda engel varsa yükselmeyi o noktada keser (bloğun içine ışınlama olmasın). */
    private double clearHeight(Location base, double wanted) {
        World w = base.getWorld();
        int bx = base.getBlockX(), bz = base.getBlockZ(), by = base.getBlockY();
        int maxY = w.getMaxHeight() - 2;
        double ok = 0;
        for (int i = 1; i <= (int) Math.ceil(wanted); i++) {
            int y = by + i;
            if (y + 1 > maxY) break;
            if (!w.getBlockAt(bx, y, bz).isPassable() || !w.getBlockAt(bx, y + 1, bz).isPassable()) break;
            ok = i;
        }
        return Math.min(wanted, ok);
    }

    private void tick(Run r) {
        Player p = r.player;
        if (!p.isOnline() || p.isDead()) { end(p.getUniqueId()); return; }
        if (!p.getWorld().equals(r.base.getWorld())) { finish(r); return; }

        int t = r.t++;
        double off = 0;
        if (t >= r.freeze) {
            double k = Math.min(1.0, (t - r.freeze) / (double) r.rise);
            double eased = k * k * (3 - 2 * k);                 // yumuşak kalkış
            off = eased * clearHeight(r.base, r.height);
        }

        Location cur = p.getLocation();
        Location to = new Location(r.base.getWorld(), r.base.getX(), r.base.getY() + off, r.base.getZ(),
                cur.getYaw(), cur.getPitch());
        double dx = cur.getX() - to.getX(), dy = cur.getY() - to.getY(), dz = cur.getZ() - to.getZ();
        if (t >= r.freeze || dx * dx + dy * dy + dz * dz > 0.01) p.teleport(to);
        p.setVelocity(new Vector(0, 0, 0));
        p.setFallDistance(0f);

        rings(r, to, t);
        if (r.darkTick >= 0 && t == r.darkTick) darken(r);
        if (r.darkened && SMOKE != null && t % 2 == 0)
            to.getWorld().spawnParticle(SMOKE, to.getX(), to.getY() + 1.0, to.getZ(), 6, 0.7, 0.6, 0.7, 0.01);

        if (t == r.freeze) {
            to.getWorld().playSound(to, "block.beacon.power_select", 1.2f, 0.6f);
            to.getWorld().playSound(to, "entity.ender_dragon.growl", 0.8f, 1.4f);
        }
        if (t > r.freeze && t % 8 == 0)
            to.getWorld().playSound(to, "block.beacon.ambient", 1.0f, 0.5f + (t - r.freeze) / (float) r.rise);

        if (t >= r.freeze + r.rise) finish(r);
    }

    /** Oyuncunun çevresinde dönen zıt yönlü kırmızı halkalar + yükselirken alta dökülen kıvılcım. */
    private void rings(Run r, Location at, int t) {
        World w = at.getWorld();
        if (DUST != null) {
            double rad = r.radius * Math.min(1.0, (t + 1) / 12.0);   // halka ilk 12 tikte açılır
            double spin = t * 0.28;
            int points = 20;
            for (int i = 0; i < points; i++) {
                double a = spin + i * (Math.PI * 2 / points);
                w.spawnParticle(DUST, at.getX() + Math.cos(a) * rad, at.getY() + 0.1, at.getZ() + Math.sin(a) * rad,
                        1, 0, 0, 0, 0, RED);
                double b = -spin * 1.3 + i * (Math.PI * 2 / points);
                w.spawnParticle(DUST, at.getX() + Math.cos(b) * rad * 0.8, at.getY() + 1.0, at.getZ() + Math.sin(b) * rad * 0.8,
                        1, 0, 0, 0, 0, DARK_RED);
                if (i % 2 == 0)
                    w.spawnParticle(DUST, at.getX() + Math.cos(a + 1.0) * rad, at.getY() + 1.9, at.getZ() + Math.sin(a + 1.0) * rad,
                            1, 0, 0, 0, 0, RED);
            }
        }
        if (t >= r.freeze && SPARK != null)
            w.spawnParticle(SPARK, at.getX(), at.getY() + 0.05, at.getZ(), 4, 0.35, 0.05, 0.35, 0.01);
    }

    /** Etraf kararır: oyuncu kör olur, gece gökyüzü/ışık yakındaki oyunculara da yansır (yalnızca istemci tarafı). */
    private void darken(Run r) {
        r.darkened = true;
        Player p = r.player;
        int remain = Math.max(20, (r.freeze + r.rise) - r.t + 40);
        try {
            if (BLIND != null) p.addPotionEffect(new PotionEffect(BLIND, remain, 0, false, false), true);
            if (DARKNESS != null) p.addPotionEffect(new PotionEffect(DARKNESS, remain, 0, false, false), true);
        } catch (Throwable ignored) { }
        try {
            double rr = (double) r.darkRadius * r.darkRadius;
            for (Player o : p.getWorld().getPlayers()) {
                if (o.getLocation().distanceSquared(r.base) > rr) continue;
                o.setPlayerTime(18000L, false);                 // gece yarısı (yalnızca o oyuncunun istemcisi)
                r.darkPlayers.add(o.getUniqueId());
            }
        } catch (Throwable ignored) { }
        p.getWorld().playSound(r.base, "entity.wither.spawn", 0.6f, 0.6f);
    }

    private void finish(Run r) {
        Player p = r.player;
        UUID id = p.getUniqueId();
        if (p.isOnline() && SPARK != null) {
            Location at = p.getLocation();
            at.getWorld().playSound(at, "entity.lightning_bolt.thunder", 1.0f, 1.2f);
            at.getWorld().spawnParticle(SPARK, at.getX(), at.getY() + 1, at.getZ(), 60, 0.4, 0.8, 0.4, 0.12);
        }
        end(id);
        if (p.isOnline()) plugin.sanctions().kickBanned(p, r.until, r.need);
    }

    /** Sahneyi bitirir ve oyuncu durumunu geri verir (walkSpeed dosyaya kalıcı yazıldığı için şart). */
    private void end(UUID id) {
        Run r = runs.remove(id);
        if (r == null) return;
        if (r.task != null) r.task.cancel();
        r.data.voidSeq = false;
        Player p = r.player;
        p.setWalkSpeed(r.walkSpeed);
        if (r.darkened) {
            try {
                if (BLIND != null) p.removePotionEffect(BLIND);
                if (DARKNESS != null) p.removePotionEffect(DARKNESS);
            } catch (Throwable ignored) { }
            for (UUID u : r.darkPlayers) {
                Player o = plugin.getServer().getPlayer(u);
                if (o != null) { try { o.resetPlayerTime(); } catch (Throwable ignored) { } }
            }
        }
        p.setGravity(r.gravity);
        p.setInvulnerable(r.invulnerable);
    }

    /** Oyuncu sahne sırasında çıktı: durumu geri ver (ban zaten kayıtlı). */
    void onQuit(UUID id) { end(id); }

    void shutdown() {
        for (UUID id : new ArrayList<UUID>(runs.keySet())) end(id);
    }
}
