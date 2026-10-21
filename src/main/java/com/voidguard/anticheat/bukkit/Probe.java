package com.voidguard.anticheat.bukkit;

import java.util.Collection;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Sunucu tarafı çarpışma/ortam sorguları. Yüklü olmayan chunk'a ASLA dokunmaz (chunk yüklemez). */
final class Probe {
    static final double HALF = 0.3, EPS = 1.0E-4;

    private Probe() {}

    private static int floor(double v) { return (int) Math.floor(v); }

    /** Ayakların altında (tolBelow içinde) taşıyıcı bir çarpışma yüzeyi var mı? Yüklenmemiş chunk -> true (muhafazakâr). */
    static boolean supported(World w, double x, double y, double z, double tolBelow) {
        int x0 = floor(x - HALF + EPS), x1 = floor(x + HALF - EPS);
        int z0 = floor(z - HALF + EPS), z1 = floor(z + HALF - EPS);
        int yTop = floor(y + 0.003), yBot = floor(y - tolBelow) - 1;   // çit/duvar 1.5 yüksek olabilir
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return true;
                for (int by = yTop; by >= yBot; by--) {
                    Block b = w.getBlockAt(bx, by, bz);
                    Material m = b.getType();
                    if (Compat.isAir(m) || b.isPassable()) continue;
                    // DİKKAT: her zaman gerçek çarpışma şekli. Çamur (0.875), ruh kumu, tarla, patika tam küp DEĞİLDİR.
                    double top = Double.NEGATIVE_INFINITY;
                    for (double[] bb : Compat.collisionBoxes(b)) {   // {minX,minY,minZ,maxX,maxY,maxZ}
                        if (bx + bb[3] <= x - HALF + EPS || bx + bb[0] >= x + HALF - EPS
                                || bz + bb[5] <= z - HALF + EPS || bz + bb[2] >= z + HALF - EPS) continue;
                        top = Math.max(top, by + bb[4]);
                    }
                    if (top >= y - tolBelow && top <= y + 0.003) return true;
                }
            }
        }
        return false;
    }

    /** Bot/shulker/vagon üstünde durma (blok yok ama gerçek zemin). Yalnızca gerekirse çağrılır. */
    static boolean entitySupport(Player p, double x, double y, double z) {
        Collection<Entity> list = p.getWorld().getNearbyEntities(new Location(p.getWorld(), x, y - 0.15, z), 0.4, 0.2, 0.4);
        for (Entity e : list) {
            if (e != p && (e instanceof Boat || e instanceof Shulker || e instanceof Minecart)) return true;
        }
        return false;
    }

    /** Hareket kontrollerinin "zeminde duruyor" bilgisi (sunucu hesabı). */
    static boolean standing(Player p, double x, double y, double z) {
        World w = p.getWorld();
        if (supported(w, x, y, z, 0.02)) return true;
        return p.isOnGround() && entitySupport(p, x, y, z);
    }

    /** NoFall için geniş (lag toleranslı) taşıyıcı sorgusu. */
    static boolean supportedLoose(Player p, double x, double y, double z) {
        return supported(p.getWorld(), x, y, z, 0.35) || entitySupport(p, x, y, z);
    }

    /** Oyuncu kutusu katı blokla kesişiyor mu (Phase). */
    static boolean insideSolid(World w, double x, double y, double z, double height) {
        double minX = x - HALF + 0.06, maxX = x + HALF - 0.06, minZ = z - HALF + 0.06, maxZ = z + HALF - 0.06;
        double minY = y + 0.1, maxY = y + height - 0.1;
        for (int bx = floor(minX); bx <= floor(maxX - EPS); bx++) {
            for (int bz = floor(minZ); bz <= floor(maxZ - EPS); bz++) {
                if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return false;
                for (int by = floor(minY); by <= floor(maxY - EPS); by++) {
                    Block b = w.getBlockAt(bx, by, bz);
                    Material m = b.getType();
                    if (Compat.isAir(m) || b.isPassable()) continue;
                    for (double[] bb : Compat.collisionBoxes(b)) {
                        if (bx + bb[0] < maxX && bx + bb[3] > minX
                                && by + bb[1] < maxY && by + bb[4] > minY
                                && bz + bb[2] < maxZ && bz + bb[5] > minZ) return true;
                    }
                }
            }
        }
        return false;
    }

    /** Çevredeki piston / düşen blok benzeri geçici katılar var mı (Phase yanlış pozitifini önler). */
    static boolean movingBlocksNear(World w, double x, double y, double z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 2; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    int bx = floor(x) + dx, bz = floor(z) + dz;
                    if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return true;
                    Material m = w.getBlockAt(bx, floor(y) + dy, bz).getType();
                    if (Compat.isPistonPart(m)) return true;
                }
        return false;
    }

    /**
     * Özel ortam (sıvı, tırmanma, ağ, bal/slime/yatak sıçraması, piston...). Çıkıştan sonra kısa süre "özel" kalır.
     * Durumu PlayerData üzerindeki süre alanlarında tutar.
     */
    static boolean special(Player p, PlayerData d, double x, double y, double z, long tick) {
        World w = p.getWorld();
        if (Compat.inWater(p) || Compat.inLava(p) || p.isSwimming()) d.liquidUntilTick = tick + 12;
        Block feet = w.getBlockAt(floor(x), floor(y), floor(z));
        Block head = w.getBlockAt(floor(x), floor(y + 1.0), floor(z));
        Block below = w.getBlockAt(floor(x), floor(y - 0.2), floor(z));
        Material f = feet.getType(), h = head.getType(), b = below.getType();

        if (Compat.isClimbable(f) || Compat.isClimbable(h) || Compat.isClimbable(b)) d.climbUntilTick = tick + 8;
        if (Compat.isSlowingBlock(f) || Compat.isSlowingBlock(h) || Compat.isSlowingBlock(b))
            d.climbUntilTick = Math.max(d.climbUntilTick, tick + 12);
        if (Compat.isBouncy(b)) d.bounceUntilTick = tick + 30;
        if (Compat.isPistonMoving(f) || Compat.isPistonMoving(b) || Compat.isPistonMoving(h)) d.pistonUntilTick = tick + 15;
        return tick < d.liquidUntilTick || tick < d.climbUntilTick || tick < d.bounceUntilTick || tick < d.pistonUntilTick;
    }

    static boolean ice(PlayerData d, World w, double x, double y, double z, long tick) {
        Material m = w.getBlockAt(floor(x), floor(y - 0.5), floor(z)).getType();
        if (Compat.isIce(m))
            d.iceUntilTick = tick + 25;
        return tick < d.iceUntilTick;
    }

    /** Ayak altı sıvı, ayak bloğu sıvı değil (sıvının ÜSTÜNDE). */
    static boolean liquidBelow(World w, double x, double y, double z) {
        Block below = w.getBlockAt(floor(x), floor(y - 0.1), floor(z));
        Block feet = w.getBlockAt(floor(x), floor(y), floor(z));
        return below.isLiquid() && !feet.isLiquid();
    }

    /** Yanda katı blok var mı (Spider: duvara tırmanma). */
    static boolean nearWall(World w, double x, double y, double z) {
        double[][] o = {{0.45, 0}, {-0.45, 0}, {0, 0.45}, {0, -0.45}};
        for (double[] d : o) {
            int bx = floor(x + d[0]), bz = floor(z + d[1]);
            if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return false;
            for (double yo : new double[]{0.5, 1.4}) {
                Block b = w.getBlockAt(bx, floor(y + yo), bz);
                if (!Compat.isAir(b.getType()) && !b.isPassable()) return true;
            }
        }
        return false;
    }

    /** Knockback yönünde 1.2 blokta engel veya tavan var mı? Varsa knockback ölçülmez. */
    static boolean knockbackBlocked(Player p, Vector v) {
        World w = p.getWorld();
        Vector h = new Vector(v.getX(), 0, v.getZ());
        if (h.lengthSquared() > 0.04) {
            h.normalize();
            for (double yo : new double[]{0.1, 0.9, 1.6}) {
                RayTraceResult rr = w.rayTraceBlocks(p.getLocation().add(0, yo, 0), h, 1.2, org.bukkit.FluidCollisionMode.NEVER, true);
                if (rr != null) return true;
            }
        }
        if (v.getY() > 0.1) {
            RayTraceResult rr = w.rayTraceBlocks(p.getLocation().add(0, 1.8, 0), new Vector(0, 1, 0), 0.8, org.bukkit.FluidCollisionMode.NEVER, true);
            if (rr != null) return true;
        }
        return false;
    }

    // -------- öznitelik / efekt yardımcıları (sürümler arası ad farkları Compat'ta çözülür) --------

    private static final Attribute MOVE = Compat.attribute("GENERIC_MOVEMENT_SPEED", "MOVEMENT_SPEED");
    private static final Attribute REACH = Compat.attribute("PLAYER_ENTITY_INTERACTION_RANGE", "ENTITY_INTERACTION_RANGE");
    private static final PotionEffectType SPEED = Compat.effect("SPEED");
    private static final PotionEffectType SLOWNESS = Compat.effect("SLOWNESS", "SLOW");
    private static final PotionEffectType JUMP = Compat.effect("JUMP_BOOST", "JUMP");
    private static final PotionEffectType LEVITATION = Compat.effect("LEVITATION");
    private static final PotionEffectType SLOW_FALLING = Compat.effect("SLOW_FALLING");

    private static PotionEffect effectOf(Player p, PotionEffectType t) { return t == null ? null : p.getPotionEffect(t); }

    /** Saldırı menzili (varsayılan 3.0). */
    static double entityReach(Player p) {
        if (REACH == null) return 3.0;
        AttributeInstance in = p.getAttribute(REACH);
        return in == null ? 3.0 : in.getValue();
    }

    /** Hız çarpanı: öznitelik değeri (efekt + sprint + özel modifier'lar dahil) / beklenen taban. */
    static double speedMul(Player p) {
        if (MOVE != null) {
            AttributeInstance in = p.getAttribute(MOVE);
            if (in != null) {
                double v = in.getValue();
                double base = p.isSprinting() ? 0.13 : 0.1;
                return Math.max(0.5, v / base);
            }
        }
        double mul = p.getWalkSpeed() / 0.2;
        PotionEffect sp = effectOf(p, SPEED);
        if (sp != null) mul *= 1 + 0.2 * (sp.getAmplifier() + 1);
        PotionEffect sl = effectOf(p, SLOWNESS);
        if (sl != null) mul *= Math.max(0.1, 1 - 0.15 * (sl.getAmplifier() + 1));
        return Math.max(0.5, mul);
    }

    static int jumpLevel(Player p) {
        PotionEffect e = effectOf(p, JUMP);
        return e == null ? 0 : e.getAmplifier() + 1;
    }

    static boolean gravityAltered(Player p) {
        return (LEVITATION != null && p.hasPotionEffect(LEVITATION)) || (SLOW_FALLING != null && p.hasPotionEffect(SLOW_FALLING));
    }
}
