package com.voidguard.anticheat.checks;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.MoveCtx;
import com.voidguard.anticheat.math.MoveMath;
import com.voidguard.anticheat.util.ServerUtil;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Speed: tek hareket olayındaki yatay mesafeyi (blok/tick) sprint-zıplama sınırıyla karşılaştırır.
 * SPEED efekti, walkSpeed, buz ve olaylar arası gecikme (lag) hesaba katılır; ardışık ihlal (buffer) gerekir.
 */
public final class SpeedCheck extends Check {
    private final Map<UUID, double[]> buf = new HashMap<>();

    public SpeedCheck(VoidGuard plugin) {
        super(plugin, "Speed", "speed");
    }

    @Override
    public void onMove(MoveCtx c) {
        Player p = c.player;
        double[] s = buf.computeIfAbsent(p.getUniqueId(), k -> new double[1]);
        if (c.exempt() || c.inLiquid()) {
            s[0] = 0;
            return;
        }
        double dh = c.dh;
        if (dh < 0.05) {
            s[0] = Math.max(0, s[0] - 0.5);
            return;
        }
        int amp = ServerUtil.effectAmplifier(p, "SPEED");
        double ratio = p.getWalkSpeed() / 0.2;
        double base = d("base-speed", 0.36);
        double tol = d("tolerance", 0.05);
        double scale = MoveMath.tickScale(c.dtMs, 5);
        double limit = MoveMath.maxHorizontalSpeed(base, amp, ratio, false, 1.0, tol) * scale;
        if (dh > limit && c.onIce()) {
            limit = MoveMath.maxHorizontalSpeed(base, amp, ratio, true, d("ice-multiplier", 1.6), tol) * scale;
        }
        if (dh > limit) {
            s[0] += 1;
            if (s[0] >= d("buffer", 6)) {
                double over = dh / limit;
                flag(p, 1.0 + Math.min(3.0, (over - 1.0) * 4.0),
                        String.format(Locale.ROOT, "%.3f blok/tick > %.3f (efekt=%d)", dh, limit, amp));
                s[0] = d("buffer", 6) * 0.5;
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
