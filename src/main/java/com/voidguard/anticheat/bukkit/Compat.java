package com.voidguard.anticheat.bukkit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Sürümler arası uyumluluk (1.13 - 1.21.x). Eklenti 1.13 API'sine karşı derlenir; sonradan eklenen,
 * yeniden adlandırılan veya kaldırılan her şey burada yansıma / isim çözümleme ile kullanılır.
 * Hiçbir çağrı NoSuchMethodError / NoSuchFieldError üretmez; bulunamazsa güvenli varsayılana düşer.
 */
final class Compat {
    private Compat() {}

    private static Method find(Class<?> c, String name, Class<?>... params) {
        try { return c.getMethod(name, params); } catch (Throwable t) { return null; }
    }

    // ---- sunucu / oyuncu ----
    private static final Method GET_TPS = find(Bukkit.class, "getTPS");
    private static final Method GET_PING = find(Player.class, "getPing");
    private static final Method IN_WATER = find(Player.class, "isInWater");
    private static final Method IN_LAVA = find(Player.class, "isInLava");

    static double tps() {
        if (GET_TPS == null) return 20.0;
        try {
            double[] t = (double[]) GET_TPS.invoke(null);
            return t != null && t.length > 0 ? Math.min(20.0, t[0]) : 20.0;
        } catch (Throwable t) { return 20.0; }
    }

    /** Ping (ms); desteklenmiyorsa 0 (ping toleransı uygulanmaz). */
    static int ping(Player p) {
        if (GET_PING == null) return 0;
        try { return (Integer) GET_PING.invoke(p); } catch (Throwable t) { return 0; }
    }

    static boolean inWater(Player p) {
        if (IN_WATER != null) { try { return (Boolean) IN_WATER.invoke(p); } catch (Throwable ignored) { } }
        Material m = p.getLocation().getBlock().getType();
        return m == Material.WATER || name(m).equals("BUBBLE_COLUMN");
    }

    static boolean inLava(Player p) {
        if (IN_LAVA != null) { try { return (Boolean) IN_LAVA.invoke(p); } catch (Throwable ignored) { } }
        return p.getLocation().getBlock().getType() == Material.LAVA;
    }

    // ---- malzeme ----
    static String name(Material m) { return m == null ? "" : m.name(); }

    static Material mat(String... names) {
        for (String n : names) {
            try { Material m = Material.getMaterial(n); if (m != null) return m; } catch (Throwable ignored) { }
        }
        return null;
    }

    static boolean isAir(Material m) {
        String n = name(m);
        return n.equals("AIR") || n.equals("CAVE_AIR") || n.equals("VOID_AIR");
    }

    static boolean isClimbable(Material m) {
        switch (name(m)) {
            case "LADDER": case "VINE": case "SCAFFOLDING":
            case "TWISTING_VINES": case "TWISTING_VINES_PLANT":
            case "WEEPING_VINES": case "WEEPING_VINES_PLANT":
            case "CAVE_VINES": case "CAVE_VINES_PLANT":
                return true;
            default:
                return false;
        }
    }

    static boolean isBed(Material m) { return name(m).endsWith("_BED"); }

    static boolean isPistonPart(Material m) {
        String n = name(m);
        return n.equals("MOVING_PISTON") || n.equals("PISTON_HEAD") || n.equals("PISTON") || n.equals("STICKY_PISTON")
                || n.equals("PISTON_EXTENSION") || n.equals("PISTON_MOVING_PIECE");
    }

    static boolean isPistonMoving(Material m) {
        String n = name(m);
        return n.equals("MOVING_PISTON") || n.equals("PISTON_HEAD") || n.equals("PISTON_EXTENSION") || n.equals("PISTON_MOVING_PIECE");
    }

    static boolean isIce(Material m) {
        String n = name(m);
        return n.equals("ICE") || n.equals("PACKED_ICE") || n.equals("BLUE_ICE") || n.equals("FROSTED_ICE");
    }

    /** Ağ / meyve çalısı / toz kar / kabarcık sütunu / bal: yavaşlatan veya özel hareketli bloklar. */
    static boolean isSlowingBlock(Material m) {
        String n = name(m);
        return n.equals("COBWEB") || n.equals("WEB") || n.equals("SWEET_BERRY_BUSH") || n.equals("POWDER_SNOW")
                || n.equals("BUBBLE_COLUMN") || n.equals("HONEY_BLOCK");
    }

    static boolean isBouncy(Material m) {
        String n = name(m);
        return n.equals("SLIME_BLOCK") || n.equals("HONEY_BLOCK") || isBed(m);
    }

    // ---- çarpışma şekli: [minX,minY,minZ,maxX,maxY,maxZ] (blok içi göreli) ----
    private static final Method COLLISION_SHAPE = find(Block.class, "getCollisionShape");
    private static final Method BLOCK_BOX = find(Block.class, "getBoundingBox");
    private static Method shapeBoxes;

    /** Bloğun gerçek çarpışma kutuları. Eski sürümde tek kutu / tam küp'e düşer; asla null döner. */
    @SuppressWarnings("unchecked")
    static List<double[]> collisionBoxes(Block b) {
        try {
            if (COLLISION_SHAPE != null) {
                Object shape = COLLISION_SHAPE.invoke(b);
                if (shapeBoxes == null) shapeBoxes = shape.getClass().getMethod("getBoundingBoxes");
                Iterable<Object> it = (Iterable<Object>) shapeBoxes.invoke(shape);
                List<double[]> out = new ArrayList<>(2);
                for (Object bb : it) out.add(boxArray(bb));
                return out;
            }
        } catch (Throwable ignored) { }
        try {
            if (BLOCK_BOX != null) {
                Object bb = BLOCK_BOX.invoke(b);
                double[] a = boxArray(bb);
                // Getter blok koordinatına mutlak döner: göreliye çevir.
                a[0] -= b.getX(); a[3] -= b.getX(); a[1] -= b.getY(); a[4] -= b.getY(); a[2] -= b.getZ(); a[5] -= b.getZ();
                return Collections.singletonList(a);
            }
        } catch (Throwable ignored) { }
        return Collections.singletonList(new double[]{0, 0, 0, 1, 1, 1});
    }

    private static double[] boxArray(Object bb) throws Exception {
        Class<?> c = bb.getClass();
        return new double[]{
                ((Number) c.getMethod("getMinX").invoke(bb)).doubleValue(), ((Number) c.getMethod("getMinY").invoke(bb)).doubleValue(),
                ((Number) c.getMethod("getMinZ").invoke(bb)).doubleValue(), ((Number) c.getMethod("getMaxX").invoke(bb)).doubleValue(),
                ((Number) c.getMethod("getMaxY").invoke(bb)).doubleValue(), ((Number) c.getMethod("getMaxZ").invoke(bb)).doubleValue()};
    }

    // ---- öznitelik / efekt ----
    /** Attribute: 1.21.3'te sabit adları değişti (GENERIC_MOVEMENT_SPEED -> MOVEMENT_SPEED); alan adıyla ara. */
    static Attribute attribute(String... fieldNames) {
        for (String n : fieldNames) {
            try { Object o = Attribute.class.getField(n).get(null); if (o instanceof Attribute) return (Attribute) o; }
            catch (Throwable ignored) { }
        }
        return null;
    }

    static PotionEffectType effect(String... names) {
        for (String n : names) {
            try { PotionEffectType t = PotionEffectType.getByName(n); if (t != null) return t; } catch (Throwable ignored) { }
        }
        return null;
    }

    // ---- parçacık ----
    static Particle particle(String... names) {
        for (String n : names) {
            try { return Particle.valueOf(n); } catch (Throwable ignored) { }
        }
        return null;
    }

    static int minY(World w) {
        try { return (Integer) World.class.getMethod("getMinHeight").invoke(w); } catch (Throwable t) { return 0; }
    }
}
