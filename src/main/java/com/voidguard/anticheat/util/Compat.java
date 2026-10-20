package com.voidguard.anticheat.util;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Sürümler arası uyumluluk katmanı (1.13 - 1.21.x). Eklenti 1.13 API'sine karşı derlenir; sonradan
 * eklenen/değişen API'ler yansıma veya isim tabanlı kontrollerle çağrılır, böylece eski sunucularda
 * NoSuchMethodError oluşmaz ve yeni sunucularda da çalışır.
 */
public final class Compat {
    private static final Method GET_TPS = find(Bukkit.class, "getTPS");
    private static final Method GET_PING = find(Player.class, "getPing");
    private static final Method GET_MIN_HEIGHT = find(World.class, "getMinHeight");
    private static final Method OFFLINE_CACHED = find(Bukkit.class, "getOfflinePlayerIfCached", String.class);

    private Compat() {}

    private static Method find(Class<?> c, String name, Class<?>... params) {
        try {
            return c.getMethod(name, params);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 1 dakikalık TPS; desteklenmiyorsa 20.0 (lag kapısı yalnızca gecikme ölçümüne dayanır). */
    public static double tps() {
        if (GET_TPS == null) return 20.0;
        try {
            double[] t = (double[]) GET_TPS.invoke(null);
            return t != null && t.length > 0 ? t[0] : 20.0;
        } catch (Throwable t) {
            return 20.0;
        }
    }

    /** Ping (ms); desteklenmiyorsa 0 (ping toleransı uygulanmaz). */
    public static int ping(Player p) {
        if (GET_PING == null) return 0;
        try {
            return (Integer) GET_PING.invoke(p);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int minHeight(World w) {
        if (GET_MIN_HEIGHT == null) return 0;
        try {
            return (Integer) GET_MIN_HEIGHT.invoke(w);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static OfflinePlayer offlineCached(String name) {
        if (OFFLINE_CACHED == null) return null;
        try {
            return (OfflinePlayer) OFFLINE_CACHED.invoke(null, name);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isAir(Material m) {
        String n = m.name();
        return n.equals("AIR") || n.equals("CAVE_AIR") || n.equals("VOID_AIR");
    }

    /** Tag.CLIMBABLE karşılığı (1.13'te Tag yok); yeni sürümlerde eklenen bitkiler de dahil. */
    public static boolean isClimbable(Material m) {
        switch (m.name()) {
            case "LADDER": case "VINE": case "SCAFFOLDING":
            case "TWISTING_VINES": case "TWISTING_VINES_PLANT":
            case "WEEPING_VINES": case "WEEPING_VINES_PLANT":
            case "CAVE_VINES": case "CAVE_VINES_PLANT":
                return true;
            default:
                return false;
        }
    }
}
