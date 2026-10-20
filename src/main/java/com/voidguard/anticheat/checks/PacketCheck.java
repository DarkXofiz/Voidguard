package com.voidguard.anticheat.checks;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.MoveCtx;
import com.voidguard.anticheat.data.PlayerData;
import com.voidguard.anticheat.math.RateWindow;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Packet: ek bağımlılık (ProtocolLib/PacketEvents) KULLANMAZ; yalnızca Bukkit olaylarının hızını ve
 * değerlerini inceler: (1) hareket olayı hızı (Timer/Blink benzeri), (2) tek olayda aşırı uzun hareket,
 * (3) hotbar/eşya atma/etkileşim/eğilme olaylarında saniyelik aşırı oran.
 */
public final class PacketCheck extends Check {
    private static final class S {
        final RateWindow moves = new RateWindow(10_000, 400);
        final Map<String, RateWindow> actions = new HashMap<>();
        final Map<String, Long> lastFlag = new HashMap<>();
    }

    private final Map<UUID, S> states = new HashMap<>();

    public PacketCheck(VoidGuard plugin) {
        super(plugin, "Packet", "packet");
    }

    private S st(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), k -> new S());
    }

    @Override
    public void onMove(MoveCtx c) {
        Player p = c.player;
        S s = st(p);
        int n = s.moves.hit(c.now);
        long since = c.now - c.data.joinTime;
        if (since > 15_000 && (n & 15) == 0 && n > i("max-moves-per-10s", 235)) {
            flag(p, 1.5, "10 sn içinde " + n + " hareket olayı (normal ≤ ~200)");
        }
        if (!c.exempt() && c.dh > d("max-move-distance", 10.0)) {
            flag(p, 3.0, String.format(Locale.ROOT, "tek olayda %.1f blok hareket", c.dh));
        }
    }

    @Override
    public void onAction(Player p, PlayerData d, String type) {
        long now = System.currentTimeMillis();
        S s = st(p);
        RateWindow w = s.actions.computeIfAbsent(type, k -> new RateWindow(1000, 128));
        int n = w.hit(now);
        int limit;
        switch (type) {
            case "HELD": limit = i("held-per-second", 50); break;
            case "DROP": limit = i("drop-per-second", 25); break;
            case "INTERACT": limit = i("interact-per-second", 40); break;
            case "SNEAK": limit = i("sneak-per-second", 20); break;
            default: return;
        }
        if (n > limit && now - s.lastFlag.getOrDefault(type, 0L) >= 1000) {
            s.lastFlag.put(type, now);
            flag(p, 0.7, type + " olayı saniyede " + n + " (sınır " + limit + ")");
        }
    }

    @Override
    public void onQuit(UUID id) {
        states.remove(id);
    }
}
