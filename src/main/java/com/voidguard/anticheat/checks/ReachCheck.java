package com.voidguard.anticheat.checks;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.PlayerData;
import com.voidguard.anticheat.math.MoveMath;
import com.voidguard.anticheat.util.ServerUtil;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Reach: göz konumundan hedefin çarpışma kutusuna en yakın noktaya mesafe; ping'e göre genişletilmiş sınırla kıyaslanır. */
public final class ReachCheck extends Check {
    private final Map<UUID, double[]> buf = new HashMap<>();

    public ReachCheck(VoidGuard plugin) {
        super(plugin, "Reach", "reach");
    }

    @Override
    public void onAttack(Player p, PlayerData d, EntityDamageByEntityEvent e) {
        if (e.getCause() != DamageCause.ENTITY_ATTACK || p.getGameMode() == GameMode.CREATIVE) return;
        Entity victim = e.getEntity();
        if (victim.getUniqueId().equals(p.getUniqueId())) return;
        int ping = ServerUtil.ping(p);
        if (ping > i("max-ping", 350)) return;

        Vector eye = p.getEyeLocation().toVector();
        BoundingBox bb = victim.getBoundingBox();
        double cx = Math.max(bb.getMinX(), Math.min(eye.getX(), bb.getMaxX()));
        double cy = Math.max(bb.getMinY(), Math.min(eye.getY(), bb.getMaxY()));
        double cz = Math.max(bb.getMinZ(), Math.min(eye.getZ(), bb.getMaxZ()));
        double dist = eye.distance(new Vector(cx, cy, cz));
        double allowed = MoveMath.allowedReach(d("base-reach", 3.1), ping, d("per-ping-tick", 0.1), d("max-ping-leeway", 1.0));

        double[] s = buf.computeIfAbsent(p.getUniqueId(), k -> new double[1]);
        if (dist > allowed) {
            s[0] += 1;
            if (s[0] >= d("buffer", 3)) {
                flag(p, 1.0 + Math.min(3.0, dist - allowed),
                        String.format(Locale.ROOT, "mesafe %.2f > %.2f (ping %d ms)", dist, allowed, ping));
                s[0] = d("buffer", 3) - 1;
            }
        } else {
            s[0] = Math.max(0, s[0] - 0.5);
        }
    }

    @Override
    public void onQuit(UUID id) {
        buf.remove(id);
    }
}
