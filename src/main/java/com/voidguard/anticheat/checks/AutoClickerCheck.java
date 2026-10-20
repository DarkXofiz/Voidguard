package com.voidguard.anticheat.checks;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.PlayerData;
import com.voidguard.anticheat.math.ClickAnalyzer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * AutoClicker: (1) tek saniyede aşırı CPS, (2) çok sayıda tıklamada yapay derecede düzenli aralıklar.
 * Blok kırma sırasındaki kol sallamaları analiz dışıdır. Olaylar tick'e hizalı işlendiğinden eşikler gevşektir.
 */
public final class AutoClickerCheck extends Check {
    private static final class S {
        ClickAnalyzer an = new ClickAnalyzer(80);
        double cpsBuf;
        long lastFlag;
    }

    private final Map<UUID, S> states = new HashMap<>();

    public AutoClickerCheck(VoidGuard plugin) {
        super(plugin, "AutoClicker", "autoclicker");
    }

    @Override
    public void onSwing(Player p, PlayerData d) {
        S s = states.computeIfAbsent(p.getUniqueId(), k -> new S());
        long now = System.currentTimeMillis();
        if (now - d.lastBlockDamage < 3000) {
            s.an.clear();
            return;
        }
        s.an.add(now);
        int cps = s.an.cps(now, 1000);
        double max = d("max-cps", 24);
        if (cps > max) {
            s.cpsBuf += 1;
            if (s.cpsBuf >= d("cps-buffer", 3) && now - s.lastFlag >= 1000) {
                s.lastFlag = now;
                flag(p, 1.0 + (cps - max) * 0.3, "CPS " + cps + " > " + (int) max);
            }
        } else {
            s.cpsBuf = Math.max(0, s.cpsBuf - 0.25);
        }
        if (s.an.intervalCount() >= i("min-samples", 40) && s.an.meanInterval() < 140) {
            double sd = s.an.stdDev();
            if (sd < d("min-std-dev-ms", 10) && now - s.lastFlag >= 1000) {
                s.lastFlag = now;
                flag(p, 2.0, String.format(Locale.ROOT, "aşırı düzenli tıklama: sapma %.1f ms, ortalama %.0f ms", sd, s.an.meanInterval()));
                s.an.clear();
            }
        }
    }

    @Override
    public void onQuit(UUID id) {
        states.remove(id);
    }
}
