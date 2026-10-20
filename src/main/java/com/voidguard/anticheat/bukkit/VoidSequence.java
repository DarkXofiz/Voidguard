package com.voidguard.anticheat.bukkit;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Ban sahnesi: oyuncu olduğu yerde donar (hareket edemez), etrafında kırmızı partikül çemberi döner,
 * sonra yukarı doğru havalanır ve tepede ban ekranıyla sunucudan atılır.
 * Ban deftere sahne BAŞLAMADAN yazılmıştır; sahne sırasında çıkan oyuncu yine banlıdır.
 */
final class VoidSequence {
    private static final LegacyComponentSerializer SEC = LegacyComponentSerializer.legacySection();
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(220, 0, 0), 1.5f);
    private static final Particle.DustOptions DARK_RED = new Particle.DustOptions(Color.fromRGB(120, 0, 0), 1.2f);

    /** Sahne bitince / iptalde oyuncuyu eski haline döndürmek için. */
    private record Saved(float walkSpeed, boolean gravity, boolean invulnerable) { }

    private final class Run {
        final Player player;
        final PlayerData data;
        final Location base;
        final long until;
        final int need;
        final int freeze, rise;
        final double height, radius;
        final Saved saved;
        BukkitTask task;
        int t;

        Run(Player p, PlayerData d, long until, int need, int freeze, int rise, double height, double radius) {
            this.player = p; this.data = d; this.until = until; this.need = need;
            this.freeze = freeze; this.rise = rise; this.height = height; this.radius = radius;
            this.base = p.getLocation().clone();
            this.saved = new Saved(p.getWalkSpeed(), p.hasGravity(), p.isInvulnerable());
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
        int freeze = Math.max(5, c.i("sanctions.void-sequence.freeze-ticks", 30));
        int rise = Math.max(10, c.i("sanctions.void-sequence.rise-ticks", 50));
        double height = Math.max(2.0, c.d("sanctions.void-sequence.rise-height", 14.0));
        double radius = Math.max(0.6, c.d("sanctions.void-sequence.ring-radius", 1.4));

        Run r = new Run(p, d, until, need, freeze, rise, height, radius);
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
        w.playSound(r.base, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 0.8f);
        p.showTitle(Title.title(SEC.deserialize("§4§lVOIDGUARD"), SEC.deserialize("§cHile tespit edildi"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        p.sendMessage(SEC.deserialize("§c§lVoidGuard §8» §cHile kullanımı tespit edildi. Hareket edemezsin."));

        r.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(r), 0L, 1L);
    }

    /** Yukarıda engel varsa yükselmeyi o noktada keser (bloğun içine ışınlama olmasın). */
    private double clearHeight(Location base, double wanted) {
        World w = base.getWorld();
        int bx = base.getBlockX(), bz = base.getBlockZ();
        int by = base.getBlockY();
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
        if (!p.isOnline() || p.isDead()) { end(p.getUniqueId(), false); return; }
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

        if (t == r.freeze) {
            to.getWorld().playSound(to, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.6f);
            to.getWorld().playSound(to, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.4f);
        }
        if (t > r.freeze && t % 8 == 0) to.getWorld().playSound(to, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 0.5f + (t - r.freeze) / (float) r.rise);

        if (t >= r.freeze + r.rise) finish(r);
    }

    /** Oyuncunun çevresinde dönen iki zıt yönlü kırmızı halka + yükselirken alta dökülen kıvılcım. */
    private void rings(Run r, Location at, int t) {
        World w = at.getWorld();
        double grow = Math.min(1.0, (t + 1) / 12.0);          // halka ilk 12 tikte açılır
        double rad = r.radius * grow;
        double spin = t * 0.28;
        int points = 20;
        for (int i = 0; i < points; i++) {
            double a = spin + i * (Math.PI * 2 / points);
            double cx = Math.cos(a) * rad, cz = Math.sin(a) * rad;
            w.spawnParticle(Particle.DUST, at.getX() + cx, at.getY() + 0.1, at.getZ() + cz, 1, 0, 0, 0, 0, RED);
            double b = -spin * 1.3 + i * (Math.PI * 2 / points);
            w.spawnParticle(Particle.DUST, at.getX() + Math.cos(b) * rad * 0.8, at.getY() + 1.0,
                    at.getZ() + Math.sin(b) * rad * 0.8, 1, 0, 0, 0, 0, DARK_RED);
            if (i % 2 == 0) w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a + 1.0) * rad,
                    at.getY() + 1.9, at.getZ() + Math.sin(a + 1.0) * rad, 1, 0, 0, 0, 0, RED);
        }
        if (t >= r.freeze) {
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, at.getX(), at.getY() + 0.05, at.getZ(), 4, 0.35, 0.05, 0.35, 0.01);
        }
    }

    private void finish(Run r) {
        Player p = r.player;
        UUID id = p.getUniqueId();
        Location at = p.getLocation();
        if (p.isOnline()) {
            at.getWorld().playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.2f);
            at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at.getX(), at.getY() + 1, at.getZ(), 60, 0.4, 0.8, 0.4, 0.12);
        }
        end(id, false);
        if (p.isOnline()) plugin.sanctions().kickBanned(p, r.until, r.need);
    }

    /** Sahneyi bitirir ve oyuncu durumunu geri verir (walkSpeed dosyaya kalıcı yazıldığı için şart). */
    private void end(UUID id, boolean quit) {
        Run r = runs.remove(id);
        if (r == null) return;
        if (r.task != null) r.task.cancel();
        r.data.voidSeq = false;
        Player p = r.player;
        p.setWalkSpeed(r.saved.walkSpeed());
        p.setGravity(r.saved.gravity());
        p.setInvulnerable(r.saved.invulnerable());
    }

    /** Oyuncu sahne sırasında çıktı: durumu geri ver (ban zaten kayıtlı). */
    void onQuit(UUID id) { end(id, true); }

    void shutdown() {
        for (UUID id : new java.util.ArrayList<>(runs.keySet())) end(id, true);
    }
}
