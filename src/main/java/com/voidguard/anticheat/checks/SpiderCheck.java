package com.voidguard.anticheat.checks;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.MoveCtx;
import com.voidguard.anticheat.util.ServerUtil;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Spider: duvara temas ederken, merdiven/sarmaşık olmadan, zıplama süresini aşan veya
 * yerçekimine uymayan (sabit hızlı) yükselme.
 */
public final class SpiderCheck extends Check {
    private static final class S {
        int rise;
        double buf;
        double lastDy;
    }

    private final Map<UUID, S> states = new HashMap<>();

    public SpiderCheck(VoidGuard plugin) {
        super(plugin, "Spider", "spider");
    }

    @Override
    public void onMove(MoveCtx c) {
        Player p = c.player;
        S s = states.computeIfAbsent(p.getUniqueId(), k -> new S());
        double dy = c.dy;
        double prev = s.lastDy;
        s.lastDy = dy;

        if (c.exempt() || c.inLiquid() || c.onClimbable() || c.inWeb()
                || ServerUtil.effectAmplifier(p, "LEVITATION") >= 0) {
            s.rise = 0;
            s.buf = 0;
            return;
        }
        if (c.nearGround() || dy <= 0.03 || !c.touchingWall()) {
            s.rise = 0;
            s.buf = Math.max(0, s.buf - 0.25);
            return;
        }
        s.rise++;
        int jump = ServerUtil.effectAmplifier(p, "JUMP_BOOST", "JUMP");
        int maxRise = i("max-rise-ticks", 9) + (jump >= 0 ? 3 * (jump + 1) : 0);
        boolean constant = s.rise >= 3 && Math.abs(dy - prev) < 0.004 && dy > 0.05;
        if (constant || s.rise > maxRise) {
            if (c.special() || c.supportedByEntity()) {
                s.rise = 0;
                return;
            }
            s.buf += constant ? 2 : 1;
            if (s.buf >= d("buffer", 4)) {
                flag(p, 2.0, String.format(Locale.ROOT, "duvarda yükselme %d tick, dy=%.3f%s", s.rise, dy, constant ? " (sabit hız)" : ""));
                s.buf = 2;
            }
        }
    }

    @Override
    public void onQuit(UUID id) {
        states.remove(id);
    }
}
