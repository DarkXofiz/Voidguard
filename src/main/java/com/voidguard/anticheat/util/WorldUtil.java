package com.voidguard.anticheat.util;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;

/** Dünya sorguları. Yüklü olmayan chunk'ları ASLA yüklemez (sunucu ana iş parçacığını yormaz). */
public final class WorldUtil {
    private WorldUtil() {}

    public static Block block(World w, double x, double y, double z) {
        if (w == null) return null;
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        if (by < Compat.minHeight(w) || by >= w.getMaxHeight()) return null;
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return null;
        return w.getBlockAt(bx, by, bz);
    }

    public static boolean isSolid(Block b) {
        if (b == null) return false;
        Material m = b.getType();
        if (Compat.isAir(m) || b.isLiquid()) return false;
        return !b.isPassable();
    }

    public static boolean isLiquidLike(Block b) {
        if (b == null) return false;
        if (b.isLiquid()) return true;
        String n = b.getType().name();
        if (n.equals("BUBBLE_COLUMN") || n.equals("KELP") || n.equals("KELP_PLANT")
                || n.equals("SEAGRASS") || n.equals("TALL_SEAGRASS")) return true;
        BlockData bd = b.getBlockData();
        return bd instanceof Waterlogged && ((Waterlogged) bd).isWaterlogged();
    }

    /** Ayak hizasından 'depth' kadar aşağıda katı blok var mı? Yüklenmemiş alan = zemin varsay (güvenli taraf). */
    public static boolean nearGround(World w, double x, double y, double z, double depth) {
        double[] offs = {-0.3, 0.0, 0.3};
        int top = (int) Math.floor(y);
        int bottom = (int) Math.floor(y - depth);
        for (double ox : offs) {
            for (double oz : offs) {
                for (int by = top; by >= bottom; by--) {
                    Block b = block(w, x + ox, by, z + oz);
                    if (b == null) {
                        if (w == null || !w.isChunkLoaded(((int) Math.floor(x + ox)) >> 4, ((int) Math.floor(z + oz)) >> 4)) return true;
                        continue;
                    }
                    if (isSolid(b)) return true;
                }
            }
        }
        return false;
    }

    public static boolean inLiquid(World w, double x, double y, double z) {
        return isLiquidLike(block(w, x, y, z)) || isLiquidLike(block(w, x, y + 0.9, z));
    }

    public static boolean onClimbable(World w, double x, double y, double z) {
        for (double dy : new double[]{0.0, 0.9, -0.1}) {
            Block b = block(w, x, y + dy, z);
            if (b != null && Compat.isClimbable(b.getType())) return true;
        }
        return false;
    }

    public static boolean inWeb(World w, double x, double y, double z) {
        for (double dy : new double[]{0.0, 0.9}) {
            Block b = block(w, x, y + dy, z);
            if (b == null) continue;
            String n = b.getType().name();
            if (n.equals("COBWEB") || n.equals("SWEET_BERRY_BUSH") || n.equals("POWDER_SNOW")) return true;
        }
        return false;
    }

    public static boolean onIce(World w, double x, double y, double z) {
        for (double dy : new double[]{-0.2, -1.2}) {
            Block b = block(w, x, y + dy, z);
            if (b != null && b.getType().name().contains("ICE")) return true;
        }
        return false;
    }

    private static boolean specialMaterial(Material m) {
        String n = m.name();
        return n.equals("SLIME_BLOCK") || n.equals("HONEY_BLOCK") || n.endsWith("_BED")
                || n.equals("COBWEB") || n.equals("SCAFFOLDING") || n.equals("POWDER_SNOW")
                || n.equals("BUBBLE_COLUMN") || n.equals("SWEET_BERRY_BUSH") || n.equals("WATER")
                || n.equals("LAVA") || n.contains("PISTON") || n.equals("MOVING_PISTON");
    }

    /** Alt/yan 3x3 alanda sıçratan, yavaşlatan veya özel fizikli yüzey var mı? */
    public static boolean nearSpecialSurface(World w, double x, double y, double z) {
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                for (int oy = -2; oy <= 0; oy++) {
                    Block b = block(w, x + ox, y + oy, z + oz);
                    if (b != null && (specialMaterial(b.getType()) || isLiquidLike(b))) return true;
                }
            }
        }
        return false;
    }

    public static boolean touchingWall(World w, double x, double y, double z) {
        double[][] o = {{0.4, 0}, {-0.4, 0}, {0, 0.4}, {0, -0.4}};
        for (double[] d : o) {
            if (isSolid(block(w, x + d[0], y + 0.5, z + d[1])) || isSolid(block(w, x + d[0], y + 1.5, z + d[1]))) return true;
        }
        return false;
    }

    /** Tekne, shulker veya vagon üzerinde mi? Pahalı olduğu için yalnızca şüphe anında çağrılır. */
    public static boolean supportedByEntity(Player p) {
        for (Entity e : p.getNearbyEntities(0.9, 1.6, 0.9)) {
            if (e instanceof Boat || e instanceof Shulker || e instanceof Minecart) return true;
        }
        return false;
    }
}
