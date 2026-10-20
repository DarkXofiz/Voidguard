package com.voidguard.anticheat.data;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.util.WorldUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Tek bir hareket olayı için paylaşılan, tembel hesaplanan bağlam (aynı blok sorgularını tekrarlamamak için). */
public final class MoveCtx {
    public final VoidGuard plugin;
    public final Player player;
    public final PlayerData data;
    public final Location from;
    public final Location to;
    public final long now;
    public final long dtMs;
    public final double dx, dy, dz, dh;

    private Boolean exempt, nearGround, inLiquid, climbable, web, ice, entity, special, wall;

    public MoveCtx(VoidGuard plugin, Player player, PlayerData data, Location from, Location to, long now) {
        this.plugin = plugin;
        this.player = player;
        this.data = data;
        this.from = from;
        this.to = to;
        this.now = now;
        this.dtMs = data.lastMoveTime == 0 ? 50 : now - data.lastMoveTime;
        this.dx = to.getX() - from.getX();
        this.dy = to.getY() - from.getY();
        this.dz = to.getZ() - from.getZ();
        this.dh = Math.sqrt(dx * dx + dz * dz);
    }

    public boolean exempt() {
        if (exempt == null) exempt = plugin.exemptions().movementExempt(player, data, now, false);
        return exempt;
    }

    public boolean nearGround() {
        if (nearGround == null) nearGround = WorldUtil.nearGround(to.getWorld(), to.getX(), to.getY(), to.getZ(), 0.6);
        return nearGround;
    }

    public boolean inLiquid() {
        if (inLiquid == null) inLiquid = WorldUtil.inLiquid(to.getWorld(), to.getX(), to.getY(), to.getZ());
        return inLiquid;
    }

    public boolean onClimbable() {
        if (climbable == null) climbable = WorldUtil.onClimbable(to.getWorld(), to.getX(), to.getY(), to.getZ());
        return climbable;
    }

    public boolean inWeb() {
        if (web == null) web = WorldUtil.inWeb(to.getWorld(), to.getX(), to.getY(), to.getZ());
        return web;
    }

    public boolean onIce() {
        if (ice == null) ice = WorldUtil.onIce(to.getWorld(), to.getX(), to.getY(), to.getZ());
        return ice;
    }

    public boolean special() {
        if (special == null) special = WorldUtil.nearSpecialSurface(to.getWorld(), to.getX(), to.getY(), to.getZ());
        return special;
    }

    public boolean touchingWall() {
        if (wall == null) wall = WorldUtil.touchingWall(to.getWorld(), to.getX(), to.getY(), to.getZ());
        return wall;
    }

    public boolean supportedByEntity() {
        if (entity == null) entity = WorldUtil.supportedByEntity(player);
        return entity;
    }
}
