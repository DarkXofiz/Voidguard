package com.voidguard.anticheat.util;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ServerUtil {
    private static final Map<String, PotionEffectType> EFFECTS = new HashMap<>();

    private ServerUtil() {}

    public static int ping(Player p) {
        return Compat.ping(p);
    }

    public static double tps() {
        return Compat.tps();
    }

    /** Etkinin amplifier değerini döndürür, yoksa -1. Sürümler arası isim farklarına (JUMP/JUMP_BOOST) dayanıklıdır. */
    public static int effectAmplifier(Player p, String... names) {
        for (String n : names) {
            PotionEffectType type = EFFECTS.computeIfAbsent(n, k -> PotionEffectType.getByName(k));
            if (type == null) continue;
            PotionEffect pe = p.getPotionEffect(type);
            if (pe != null) return pe.getAmplifier();
        }
        return -1;
    }

    public static int enchantLevel(ItemStack item, String key) {
        if (item == null) return 0;
        try {
            Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft(key));
            return e == null ? 0 : item.getEnchantmentLevel(e);
        } catch (Throwable t) {
            return 0; // yeni sürümlerde API farkı: alt sınır daha da muhafazakâr olur
        }
    }
}
