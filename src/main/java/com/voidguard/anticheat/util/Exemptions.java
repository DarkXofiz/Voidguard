package com.voidguard.anticheat.util;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.PlayerData;
import com.voidguard.anticheat.util.ExemptionPolicy.Grace;
import com.voidguard.anticheat.util.ExemptionPolicy.MovementState;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** OP, izin, test oyuncusu ve hareket muafiyetleri. */
public final class Exemptions {
    private final VoidGuard plugin;
    private volatile boolean ops = true;
    private volatile String permission = "voidguard.bypass";
    private volatile Set<String> names = new HashSet<>();
    private volatile Grace grace = new Grace(1500, 2000, 2000, 1500);

    public Exemptions(VoidGuard plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        ops = c.getBoolean("exempt.ops", true);
        permission = c.getString("exempt.permission", "voidguard.bypass");
        Set<String> s = new HashSet<>();
        for (String n : c.getStringList("exempt.players")) s.add(n.toLowerCase(Locale.ROOT));
        names = s;
        grace = new Grace(c.getLong("tolerance.teleport-grace-ms", 1500),
                c.getLong("tolerance.velocity-grace-ms", 2000),
                c.getLong("tolerance.gamemode-grace-ms", 2000),
                c.getLong("tolerance.special-move-grace-ms", 1500));
    }

    /** Tüm kontrollerden muaf mı? (izin sorgusu 3 sn önbelleğe alınır) */
    public boolean isExempt(Player p, PlayerData d) {
        long now = System.currentTimeMillis();
        if (now - d.exemptCheckedAt > 3000) {
            d.exemptCheckedAt = now;
            d.exemptCached = computeExempt(p);
        }
        return d.exemptCached || p.getGameMode() == GameMode.SPECTATOR;
    }

    /** Anlık (önbelleksiz) sonuç, komutlar için. */
    public boolean computeExempt(Player p) {
        if (ops && p.isOp()) return true;
        if (names.contains(p.getName().toLowerCase(Locale.ROOT))) return true;
        return p.hasPermission(permission);
    }

    public boolean movementExempt(Player p, PlayerData d, long now, boolean ignoreVelocity) {
        GameMode gm = p.getGameMode();
        MovementState st = new MovementState(
                gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR,
                p.getAllowFlight(), p.isFlying(), p.isGliding(), p.isRiptiding(),
                p.isInsideVehicle(), p.isDead(), p.isSleeping(),
                now - d.lastTeleport,
                ignoreVelocity ? Long.MAX_VALUE : now - d.lastVelocity,
                Math.min(now - d.lastGamemode, now - d.lastFlightChange),
                now - d.lastSpecialMove,
                plugin.effect().isActive(p.getUniqueId()));
        return ExemptionPolicy.movementExempt(st, grace);
    }
}
