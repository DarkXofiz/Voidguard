package com.voidguard.anticheat.checks;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.MoveCtx;
import com.voidguard.anticheat.util.WorldUtil;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Jesus: derin sıvının yüzeyinde (başı dışarıda) yatay giderken dikey konumun sabit kalması.
 * Nilüfer yaprağı, tekne, buz (Frost Walker), yüzme, sığ su ve kabarcık sütunu muaftır.
 */
public final class JesusCheck extends Check {
    private final Map<UUID, int[]> ticks = new HashMap<>();

    public JesusCheck(VoidGuard plugin) {
        super(plugin, "Jesus", "jesus");
    }

    @Override
    public void onMove(MoveCtx c) {
        Player p = c.player;
        int[] t = ticks.computeIfAbsent(p.getUniqueId(), k -> new int[1]);
        if (c.exempt() || p.isSwimming()) {
            t[0] = 0;
            return;
        }
        Location to = c.to;
        World w = to.getWorld();
        Block below = WorldUtil.block(w, to.getX(), to.getY() - 0.1, to.getZ());
        Block feet = WorldUtil.block(w, to.getX(), to.getY(), to.getZ());
        Block chest = WorldUtil.block(w, to.getX(), to.getY() + 1.0, to.getZ());
        Block deep1 = WorldUtil.block(w, to.getX(), to.getY() - 1.1, to.getZ());
        Block deep2 = WorldUtil.block(w, to.getX(), to.getY() - 2.1, to.getZ());
        boolean onSurface = below != null && below.isLiquid() && deep1 != null && deep1.isLiquid()
                && deep2 != null && deep2.isLiquid() && chest != null && !chest.isLiquid();
        boolean lily = feet != null && feet.getType().name().equals("LILY_PAD");

        if (onSurface && !lily && Math.abs(c.dy) < 0.003 && c.dh > 0.08) {
            t[0]++;
        } else {
            t[0] = Math.max(0, t[0] - 2);
        }
        if (t[0] >= i("min-ticks", 10)) {
            if (c.supportedByEntity()) {
                t[0] = 0;
                return;
            }
            flag(p, 2.0, String.format(Locale.ROOT, "sıvı üzerinde yürüme (%d tick, dy=%.4f)", t[0], c.dy));
            t[0] = i("min-ticks", 10) / 2;
        }
    }

    @Override
    public void onQuit(UUID id) {
        ticks.remove(id);
    }
}
