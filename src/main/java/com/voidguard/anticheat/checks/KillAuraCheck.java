package com.voidguard.anticheat.checks;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.PlayerData;
import com.voidguard.anticheat.math.RateWindow;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.Vector;

/**
 * KillAura: (1) bakış yönü ile hedef arasında imkânsız açı, (2) kısa sürede çok sayıda farklı hedefe vuruş,
 * (3) vuruş sayısı ile kol sallama sayısı arasındaki tutarsızlık.
 */
public final class KillAuraCheck extends Check {
    private static final class S {
        double angleBuf;
        final ArrayDeque<Long> times = new ArrayDeque<>();
        final ArrayDeque<UUID> ids = new ArrayDeque<>();
        final RateWindow attacks = new RateWindow(5000, 128);
        final RateWindow swings = new RateWindow(5000, 256);
        long lastSwingFlag;
    }

    private final Map<UUID, S> states = new HashMap<>();

    public KillAuraCheck(VoidGuard plugin) {
        super(plugin, "KillAura", "killaura");
    }

    private S st(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), k -> new S());
    }

    @Override
    public void onAttack(Player p, PlayerData d, EntityDamageByEntityEvent e) {
        if (e.getCause() != DamageCause.ENTITY_ATTACK || !(e.getEntity() instanceof LivingEntity)) return;
        LivingEntity victim = (LivingEntity) e.getEntity();
        if (victim.getUniqueId().equals(p.getUniqueId())) return;
        S s = st(p);
        long now = System.currentTimeMillis();

        // 1) Açı
        Location eye = p.getEyeLocation();
        Vector toTarget = victim.getBoundingBox().getCenter().subtract(eye.toVector());
        double dist = toTarget.length();
        double maxAngle = d("max-angle", 90);
        if (dist > 1.5) {
            double angle = Math.toDegrees(eye.getDirection().angle(toTarget));
            if (angle > maxAngle) {
                s.angleBuf += 1;
                if (s.angleBuf >= d("angle-buffer", 2)) {
                    flag(p, 1.5, String.format(Locale.ROOT, "saldırı açısı %.0f° (maks %.0f°), mesafe %.1f", angle, maxAngle, dist));
                    s.angleBuf = 0;
                }
            } else {
                s.angleBuf = Math.max(0, s.angleBuf - 0.5);
            }
        }

        // 2) Çoklu hedef
        long window = (long) d("multi-window-ms", 300);
        s.times.addLast(now);
        s.ids.addLast(victim.getUniqueId());
        while (s.times.size() > 10 || (!s.times.isEmpty() && now - s.times.peekFirst() > window)) {
            s.times.pollFirst();
            s.ids.pollFirst();
        }
        Set<UUID> distinct = new HashSet<>(s.ids);
        if (distinct.size() >= i("multi-targets", 3)) {
            flag(p, 2.0, distinct.size() + " farklı hedef " + window + " ms içinde");
            s.times.clear();
            s.ids.clear();
        }

        // 3) Vuruş / kol sallama oranı (vuruş paketi sallamadan önce gelir, bu yüzden pay bırakılır)
        s.attacks.hit(now);
        int atk = s.attacks.count(now);
        int sw = s.swings.count(now);
        if (atk >= i("min-attacks-for-swing", 8) && atk - sw >= i("swing-mismatch", 5) && now - s.lastSwingFlag > 3000) {
            s.lastSwingFlag = now;
            flag(p, 1.0, "5 sn içinde " + atk + " vuruş, " + sw + " kol sallama");
        }
    }

    @Override
    public void onSwing(Player p, PlayerData d) {
        st(p).swings.hit(System.currentTimeMillis());
    }

    @Override
    public void onQuit(UUID id) {
        states.remove(id);
    }
}
