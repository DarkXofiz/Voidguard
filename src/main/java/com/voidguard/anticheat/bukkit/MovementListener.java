package com.voidguard.anticheat.bukkit;

import com.voidguard.anticheat.core.Geometry;
import com.voidguard.anticheat.core.MoveSample;
import com.voidguard.anticheat.core.Result;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

final class MovementListener implements Listener {
    private final VoidGuard plugin;

    MovementListener(VoidGuard plugin) { this.plugin = plugin; }

    private boolean flag(Player p, PlayerData d, String name, Result r) {
        if (r == null) return false;
        plugin.flagger().flag(p, d, name, r);
        return true;
    }

    /**
     * Hareket kısıtı aktifken (log sonrası anında dondurma veya ihlal kısıtı): yerinden oynayamaz, yukarı çıkamaz.
     * Yalnızca düşüşe ve bakış yönünü çevirmeye izin verilir. Sıvı/lav içindekiler tuzağa düşmesin diye serbesttir.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFreeze(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.peek(p.getUniqueId());
        if (d == null || !d.mitActive("MOVE", System.currentTimeMillis())) return;
        if (Compat.inLava(p) || Compat.inWater(p) || p.isInsideVehicle() || p.isGliding()) return;
        Location f = e.getFrom(), t = e.getTo();
        if (t == null) return;
        double dx = t.getX() - f.getX(), dy = t.getY() - f.getY(), dz = t.getZ() - f.getZ();
        if (dx * dx + dz * dz > 1.0E-6 || dy > 0.001) e.setCancelled(true);   // iptal = teleport olayı yok, bedava süre yok
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        try {
            handle(e, p);
        } catch (Throwable t) {
            // Bir hata tüm AntiCheat'i veya diğer kontrolleri durdurmasın
            plugin.getLogger().warning("Hareket kontrolü hatası (" + p.getName() + "): " + t);
        }
    }

    private void handle(PlayerMoveEvent e, Player p) {
        PlayerData d = plugin.data(p);
        Location from = e.getFrom(), to = e.getTo();
        if (to == null || to.getWorld() == null) return;
        VoidGuardConfig cfg = plugin.cfg();
        long tick = plugin.tick();
        long nowMs = VoidGuard.nowMs();

        if (!d.hasLast) {
            d.lastX = from.getX(); d.lastY = from.getY(); d.lastZ = from.getZ();
            d.lastYaw = from.getYaw(); d.lastPitch = from.getPitch();
            d.hasLast = true;
        }

        double dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        boolean moved = dx != 0 || dy != 0 || dz != 0;
        float yaw = to.getYaw(), pitch = to.getPitch();
        double dYaw = Geometry.wrapDegrees(yaw - d.lastYaw);
        double dPitch = pitch - d.lastPitch;
        d.pushYawDelta(dYaw);
        d.lastYaw = yaw; d.lastPitch = pitch;

        if (dx * dx + dy * dy + dz * dz > 100.0) d.grace(tick + cfg.i("safety.teleport-grace-ticks", 30));

        boolean exempt = plugin.exemptReason(p, d) != null;
        boolean lag = plugin.lagging(p, d);
        boolean bad = false;

        d.pushPos(nowMs, to.getX(), to.getY(), to.getZ());

        bad |= flag(p, d, "Timer", d.timer.onMove(nowMs, exempt || lag));

        if (!exempt && !lag && d.inCombat(tick) && !(d.bedrock && cfg.b("general.bedrock-relaxed", true)))
            bad |= flag(p, d, "Rotation", d.rotation.onRotation(dPitch));

        if (moved) {
            World w = to.getWorld();
            boolean standing;
            boolean special = false;
            if (exempt || lag) {
                MoveSample s = new MoveSample(tick, dx, dy, dz, yaw, pitch, p.isOnGround(), p.isOnGround(), true, false, false,
                        false, false, 1.0, 0, false, p.getFallDistance());
                standing = p.isOnGround();
                // Dikey fizik (Fly/Step/HighJump) TPS düşüşünden ETKİLENMEZ: yalnızca aşırı pingde atlanır.
                boolean airOnly = !exempt && Compat.ping(p) <= cfg.i("safety.max-ping-ms-air", 1000);
                if (airOnly) {
                    final double ax = to.getX(), ay = to.getY(), az = to.getZ();
                    boolean st = Probe.standing(p, ax, ay, az);
                    boolean sp = Probe.special(p, d, ax, ay, az, tick);
                    MoveSample as = new MoveSample(tick, dx, dy, dz, yaw, pitch, p.isOnGround(), st, false, tick < d.velocityUntilTick,
                            sp, Probe.gravityAltered(p), false, Probe.speedMul(p), Probe.jumpLevel(p), p.isHandRaised(),
                            p.getFallDistance());
                    Result ar = d.air.onMove(as);
                    if (ar != null) {
                        String an = airName(ar);
                        if (an.equals("Fly") && dy > 0 && ar.detail().startsWith("Fly") && Probe.nearWall(w, ax, ay, az)) an = "Spider";
                        bad |= flag(p, d, an, ar);
                    }
                    standing = st;
                    if (!bad && st && !sp) d.setGood(w, ax, ay, az, yaw, pitch);
                } else {
                    d.air.onMove(s);
                }
                d.speed.onMove(s); d.noFall.onMove(s, () -> true); d.noSlow.onMove(s);
                d.jesus.onMove(false, 0, 0, true);
            } else {
                final double tx = to.getX(), ty = to.getY(), tz = to.getZ();
                standing = Probe.standing(p, tx, ty, tz);
                special = Probe.special(p, d, tx, ty, tz, tick);
                boolean ice = Probe.ice(d, w, tx, ty, tz, tick);
                boolean velGrace = tick < d.velocityUntilTick;
                MoveSample s = new MoveSample(tick, dx, dy, dz, yaw, pitch, p.isOnGround(), standing, false, velGrace, special,
                        Probe.gravityAltered(p), ice, Probe.speedMul(p), Probe.jumpLevel(p), p.isHandRaised(),
                        p.getFallDistance());

                Result r = d.air.onMove(s);
                if (r != null) {
                    String name = airName(r);
                    // Yukarı yönlü Fly sapması duvar yanındaysa Spider olarak adlandır
                    if (name.equals("Fly") && dy > 0 && r.detail().startsWith("Fly") && Probe.nearWall(w, tx, ty, tz)) name = "Spider";
                    bad |= flag(p, d, name, r);
                }
                bad |= flag(p, d, "Speed", d.speed.onMove(s));
                bad |= flag(p, d, "NoFall", d.noFall.onMove(s, () -> Probe.supportedLoose(p, tx, ty, tz)));
                bad |= flag(p, d, "NoSlow", d.noSlow.onMove(s));
                bad |= flag(p, d, "Velocity", d.velocity.onMove(s, nowMs));
                d.criticals.onMove(tick, s, d.lastStanding);
                bad |= flag(p, d, "GuiMove", d.window.onMoveWhileOpen(tick, s.hSpeed(), standing, special || velGrace));

                boolean surface = !standing && !Compat.inWater(p) && !p.isSwimming() && Probe.liquidBelow(w, tx, ty, tz);
                bad |= flag(p, d, "Jesus", d.jesus.onMove(surface, dy, s.hSpeed(), false));

                if (cfg.enabled("Phase") && !special && !velGrace) bad |= phase(p, d, from, to, w);

                // Geri-ışınlama için "son temiz konum": yerde, kanıtsız, özel ortam dışı
                if (!bad && standing && !special) d.setGood(w, tx, ty, tz, yaw, pitch);
            }
            d.lastStanding = standing;
            d.lastHSpeed = Math.hypot(dx, dz);
            d.lastX = to.getX(); d.lastY = to.getY(); d.lastZ = to.getZ();
        }
    }

    private static String airName(Result r) {
        String t = r.detail();
        if (t.startsWith("Step")) return "Step";
        if (t.startsWith("HighJump")) return "HighJump";
        return "Fly";           // Fly, Hover, FastFall
    }

    private boolean phase(Player p, PlayerData d, Location from, Location to, World w) {
        double h = p.getHeight();
        boolean inside = Probe.insideSolid(w, to.getX(), to.getY(), to.getZ(), h);
        if (!inside) { d.phaseBuf.pass(); return false; }
        if (Probe.insideSolid(w, from.getX(), from.getY(), from.getZ(), h)) return false;
        if (Probe.movingBlocksNear(w, to.getX(), to.getY(), to.getZ())) return false;
        if (d.phaseBuf.fail(1)) {
            plugin.flagger().flag(p, d, "Phase", Result.of(1.0, String.format(java.util.Locale.ROOT,
                    "Phase: katı bloğa girdi (%.2f,%.2f,%.2f)", to.getX(), to.getY(), to.getZ())));
            return true;
        }
        return false;
    }
}
