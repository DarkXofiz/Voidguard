package com.voidguard.anticheat.checks;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.PlayerData;
import com.voidguard.anticheat.math.MoveMath;
import com.voidguard.anticheat.util.ServerUtil;
import com.voidguard.anticheat.util.WorldUtil;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;

/**
 * AntiKnockback: hasar kaynaklı velocity olayından sonra (ping + birkaç tick) oyuncunun geri tepme yönünde
 * neredeyse hiç ilerlememesi. Duvar/engel, sıvı, ağ, araç, yeni vuruş ve ışınlanma durumlarında değerlendirilmez.
 */
public final class AntiKnockbackCheck extends Check {
    private final Map<UUID, double[]> buf = new HashMap<>();

    public AntiKnockbackCheck(VoidGuard plugin) {
        super(plugin, "AntiKnockback", "antiknockback");
    }

    @Override
    public void onVelocity(Player p, PlayerData d, PlayerVelocityEvent e) {
        long now = System.currentTimeMillis();
        if (now - d.lastDamage > 600) return; // yalnızca hasar kaynaklı
        Vector v = e.getVelocity();
        double velH = Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
        double minVel = d("min-velocity", 0.2);
        if (velH < minVel) return;
        int ping = ServerUtil.ping(p);
        if (ping > i("max-ping", 400)) return;
        if (plugin.exemptions().movementExempt(p, d, now, true)) return;

        final Location start = p.getLocation().clone();
        final Vector dir = new Vector(v.getX() / velH, 0, v.getZ() / velH);
        if (blocked(start, dir)) return;

        final UUID id = p.getUniqueId();
        final int counter = d.velocityCounter;
        final long started = now;
        int delay = MoveMath.knockbackDelayTicks(ping, i("delay-ticks", 4), 10);
        Bukkit.getScheduler().runTaskLater(plugin, () -> evaluate(id, start, dir, velH, counter, started), delay);
    }

    private static boolean blocked(Location s, Vector dir) {
        for (double dist : new double[]{0.8, 1.4}) {
            for (double dy : new double[]{0.2, 1.2}) {
                if (WorldUtil.isSolid(WorldUtil.block(s.getWorld(), s.getX() + dir.getX() * dist, s.getY() + dy, s.getZ() + dir.getZ() * dist))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void evaluate(UUID id, Location start, Vector dir, double velH, int counter, long started) {
        Player p = Bukkit.getPlayer(id);
        PlayerData d = plugin.data().getIfPresent(id);
        if (p == null || !p.isOnline() || d == null) return;
        if (d.velocityCounter != counter || d.lastTeleport > started) return; // yeni vuruş/ışınlanma
        long now = System.currentTimeMillis();
        if (plugin.exemptions().movementExempt(p, d, now, true) || plugin.gate().skip(p)) return;
        Location cur = p.getLocation();
        if (cur.getWorld() == null || !cur.getWorld().equals(start.getWorld())) return;
        Location at = cur;
        if (WorldUtil.inLiquid(at.getWorld(), at.getX(), at.getY(), at.getZ())
                || WorldUtil.inWeb(at.getWorld(), at.getX(), at.getY(), at.getZ())) return;

        double moved = (cur.getX() - start.getX()) * dir.getX() + (cur.getZ() - start.getZ()) * dir.getZ();
        double[] s = buf.computeIfAbsent(id, k -> new double[1]);
        if (MoveMath.knockbackIgnored(velH, moved, d("min-ratio", 0.2), d("min-velocity", 0.2))) {
            s[0] += 1;
            if (s[0] >= d("buffer", 3)) {
                flag(p, 2.0, String.format(Locale.ROOT, "geri tepme hızı %.2f, ilerleme %.2f", velH, moved));
                s[0] = d("buffer", 3) - 1;
            }
        } else {
            s[0] = Math.max(0, s[0] - 1);
        }
    }

    @Override
    public void onQuit(UUID id) {
        buf.remove(id);
    }
}
