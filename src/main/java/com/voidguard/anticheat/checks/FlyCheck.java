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
 * Fly: (1) zıplama sınırını aşan yükseklik, (2) havada sabit dikey konum (süzülme/hover),
 * (3) yerçekimi tahminine aykırı (beklenenden yüksek) dikey hız. Uçuş izni, elytra, sıvı, merdiven,
 * ağ, araç, geri tepme, ışınlanma ve sıçratan yüzeyler muaftır.
 */
public final class FlyCheck extends Check {
    private static final class S {
        int airTicks;
        double groundY;
        double buf;
        double lastDy;
        long lastTime;
        boolean lastSingle;
    }

    private final Map<UUID, S> states = new HashMap<>();

    public FlyCheck(VoidGuard plugin) {
        super(plugin, "Fly", "fly");
    }

    @Override
    public void onMove(MoveCtx c) {
        Player p = c.player;
        S s = states.computeIfAbsent(p.getUniqueId(), k -> new S());
        long dt = s.lastTime == 0 ? 0 : c.now - s.lastTime;
        boolean single = dt >= 30 && dt <= 75;
        double dy = c.dy;
        double prevDy = s.lastDy;
        boolean prevSingle = s.lastSingle;
        s.lastDy = dy;
        s.lastTime = c.now;
        s.lastSingle = single;

        if (c.exempt() || c.inLiquid() || c.onClimbable() || c.inWeb()
                || ServerUtil.effectAmplifier(p, "LEVITATION", "SLOW_FALLING") >= 0) {
            reset(s);
            return;
        }
        if (c.nearGround()) {
            s.airTicks = 0;
            s.groundY = c.to.getY();
            s.buf = Math.max(0, s.buf - 0.25);
            return;
        }
        if (s.airTicks == 0) s.groundY = c.from.getY();
        s.airTicks++;

        double add = 0;
        String ev = null;

        int jump = ServerUtil.effectAmplifier(p, "JUMP_BOOST", "JUMP");
        double maxH = MoveMath.maxJumpHeight(jump) + d("height-tolerance", 0.6);
        double height = c.to.getY() - s.groundY;
        if (height > maxH && dy > 0) {
            add = 2.0;
            ev = String.format(Locale.ROOT, "yükseklik %.2f > %.2f", height, maxH);
        } else if (s.airTicks > i("hover-ticks", 10) && Math.abs(dy) < 0.003) {
            add = 1.0;
            ev = String.format(Locale.ROOT, "havada sabit (%d tick, dy=%.4f)", s.airTicks, dy);
        } else if (single && prevSingle && s.airTicks > 6) {
            double expected = MoveMath.predictedFallVelocity(prevDy);
            if (dy > expected + d("gravity-tolerance", 0.05)) {
                add = 1.0;
                ev = String.format(Locale.ROOT, "yerçekimi dışı dy=%.3f beklenen=%.3f", dy, expected);
            }
        }

        if (add <= 0) {
            s.buf = Math.max(0, s.buf - 0.15);
            return;
        }
        if (c.supportedByEntity() || c.special()) { // tekne, slime, bal vb. meşru durumlar
            reset(s);
            return;
        }
        s.buf += add;
        double max = d("buffer", 6);
        if (s.buf >= max) {
            flag(p, add >= 2.0 ? 2.0 : 1.0, ev);
            s.buf = max * 0.5;
        }
    }

    private static void reset(S s) {
        s.airTicks = 0;
        s.buf = 0;
    }

    @Override
    public void onQuit(UUID id) {
        states.remove(id);
    }
}
