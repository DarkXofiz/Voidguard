package com.voidguard.anticheat.checks;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.PlayerData;
import com.voidguard.anticheat.math.RateWindow;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Scaffold: (1) saniyedeki yerleştirme sayısı, (2) yerleştirilen bloğa bakmadan yerleştirme (açı),
 * (3) aşağı blok koyarken bakış yukarıda, (4) bakış ışınının yerleştirilen yüzeyi tutmaması.
 */
public final class ScaffoldCheck extends Check {
    private static final class S {
        final RateWindow places = new RateWindow(1000, 64);
        double angleBuf, pitchBuf, rayBuf;
    }

    private final Map<UUID, S> states = new HashMap<>();

    public ScaffoldCheck(VoidGuard plugin) {
        super(plugin, "Scaffold", "scaffold");
    }

    @Override
    public void onBlockPlace(Player p, PlayerData d, BlockPlaceEvent e) {
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        S s = states.computeIfAbsent(p.getUniqueId(), k -> new S());
        long now = System.currentTimeMillis();
        Block placed = e.getBlockPlaced();
        Block against = e.getBlockAgainst();

        int rate = s.places.hit(now);
        if (rate > i("max-places-per-second", 12)) {
            flag(p, 1.0, "saniyede " + rate + " blok yerleştirme");
        }

        Location eye = p.getEyeLocation();
        Vector toAgainst = against.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(eye.toVector());
        double angle = Math.toDegrees(eye.getDirection().angle(toAgainst));
        double maxAngle = d("max-angle", 80);
        if (angle > maxAngle) {
            s.angleBuf += 1;
            if (s.angleBuf >= 3) {
                flag(p, 1.5, String.format(Locale.ROOT, "bakılmayan bloğa yerleştirme: açı %.0f° > %.0f°", angle, maxAngle));
                s.angleBuf = 0;
            }
        } else {
            s.angleBuf = Math.max(0, s.angleBuf - 0.5);
        }

        Location loc = p.getLocation();
        double hx = placed.getX() + 0.5 - loc.getX();
        double hz = placed.getZ() + 0.5 - loc.getZ();
        boolean below = placed.getY() < Math.floor(loc.getY()) && Math.sqrt(hx * hx + hz * hz) < 1.6;
        if (below && loc.getPitch() < d("min-pitch", 25)) {
            s.pitchBuf += 1;
            if (s.pitchBuf >= d("buffer", 3)) {
                flag(p, 1.5, String.format(Locale.ROOT, "ayak altına yerleştirirken pitch %.0f°", loc.getPitch()));
                s.pitchBuf = 0;
            }
        } else {
            s.pitchBuf = Math.max(0, s.pitchBuf - 0.5);
        }

        if (b("ray-check", true)) {
            RayTraceResult r = p.rayTraceBlocks(6.0);
            Block hit = r == null ? null : r.getHitBlock();
            if (hit == null || !hit.equals(against)) {
                s.rayBuf += 1;
                if (s.rayBuf >= d("ray-buffer", 6)) {
                    flag(p, 1.0, "bakış ışını yerleştirilen yüzeyi tutmuyor");
                    s.rayBuf = 0;
                }
            } else {
                s.rayBuf = Math.max(0, s.rayBuf - 0.5);
            }
        }
    }

    @Override
    public void onQuit(UUID id) {
        states.remove(id);
    }
}
