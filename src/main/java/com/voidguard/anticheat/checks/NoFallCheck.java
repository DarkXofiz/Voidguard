package com.voidguard.anticheat.checks;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.MoveCtx;
import com.voidguard.anticheat.data.PlayerData;
import com.voidguard.anticheat.util.ServerUtil;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * NoFall: (1) istemci "yerdeyim" derken sunucu tarafında yer bulunmayan düşüş (sahte onGround),
 * (2) belirgin yükseklikten güvenli bir yüzeye inip hiç düşme hasarı olmaması.
 */
public final class NoFallCheck extends Check {
    private static final class S {
        double buf;
        boolean air;
        double peakY;
        long lastFallDamage;
    }

    private final Map<UUID, S> states = new HashMap<>();

    public NoFallCheck(VoidGuard plugin) {
        super(plugin, "NoFall", "nofall");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onMove(MoveCtx c) {
        Player p = c.player;
        S s = states.computeIfAbsent(p.getUniqueId(), k -> new S());
        if (c.exempt() || c.inLiquid() || c.onClimbable() || c.inWeb() || c.special()) {
            s.air = false;
            s.buf = 0;
            return;
        }
        boolean ground = c.nearGround();

        // 1) Sahte onGround
        if (p.isOnGround() && !ground && c.dy < -0.1) {
            if (!c.supportedByEntity()) {
                s.buf += 1;
                if (s.buf >= d("buffer", 3)) {
                    flag(p, 2.0, String.format(Locale.ROOT, "istemci yerde diyor ama altta blok yok (dy=%.2f)", c.dy));
                    s.buf = 1;
                }
            }
        } else {
            s.buf = Math.max(0, s.buf - 0.5);
        }

        // 2) Düşüş izleme ve hasar doğrulama
        if (!ground) {
            if (!s.air) {
                s.air = true;
                s.peakY = c.from.getY();
            }
            s.peakY = Math.max(s.peakY, c.to.getY());
        } else if (s.air) {
            s.air = false;
            double fall = s.peakY - c.to.getY();
            if (b("check-damage", true) && fall >= d("min-fall-distance", 6.0)) {
                final long landing = c.now;
                final long dmgBefore = c.data.lastDamage;
                final UUID id = p.getUniqueId();
                final double f = fall;
                int delay = 6 + Math.min(10, (int) Math.ceil(ServerUtil.ping(p) / 50.0));
                Bukkit.getScheduler().runTaskLater(plugin, () -> verify(id, landing, dmgBefore, f), delay);
            }
        }
    }

    private void verify(UUID id, long landing, long dmgBefore, double fall) {
        Player p = Bukkit.getPlayer(id);
        PlayerData pd = plugin.data().getIfPresent(id);
        S s = states.get(id);
        if (p == null || !p.isOnline() || pd == null || s == null) return;
        if (s.lastFallDamage >= landing - 250) return;               // hasar alındı: normal
        if (landing - dmgBefore < 1000) return;                       // başka hasar nedeniyle dokunulmazlık
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (p.isInvulnerable() || p.isDead() || p.isInsideVehicle() || p.getAllowFlight()) return;
        if (ServerUtil.effectAmplifier(p, "DAMAGE_RESISTANCE", "RESISTANCE") >= 4) return;
        if (ServerUtil.effectAmplifier(p, "SLOW_FALLING") >= 0 || ServerUtil.effectAmplifier(p, "LEVITATION") >= 0) return;
        if (plugin.exemptions().movementExempt(p, pd, System.currentTimeMillis(), true)) return;
        int jump = ServerUtil.effectAmplifier(p, "JUMP_BOOST", "JUMP");
        if (fall - (jump + 1) < d("min-fall-distance", 6.0) - 1.0) return;
        flag(p, 3.0, String.format(Locale.ROOT, "%.1f blok düştü, düşme hasarı yok", fall));
    }

    @Override
    public void onDamage(Player p, PlayerData d, EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            states.computeIfAbsent(p.getUniqueId(), k -> new S()).lastFallDamage = System.currentTimeMillis();
        }
    }

    @Override
    public void onQuit(UUID id) {
        states.remove(id);
    }
}
