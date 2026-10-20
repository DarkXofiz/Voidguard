package com.voidguard.anticheat.math;

/** Bukkit'ten bağımsız, test edilebilen hareket ve mesafe matematiği. */
public final class MoveMath {
    private MoveMath() {}

    public static double horizontal(double dx, double dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Vanilla dikey hız güncellemesi: (vy - 0.08) * 0.98 */
    public static double predictedFallVelocity(double lastDy) {
        return (lastDy - 0.08) * 0.98;
    }

    /** Verilen zıplama gücünde (JUMP_BOOST amplifier, yoksa -1) ulaşılabilecek en yüksek zıplama yüksekliği. */
    public static double maxJumpHeight(int jumpAmp) {
        double vy = 0.42 + (jumpAmp >= 0 ? 0.1 * (jumpAmp + 1) : 0.0);
        double y = 0.0;
        while (vy > 0) {
            y += vy;
            vy = (vy - 0.08) * 0.98;
        }
        return y;
    }

    /** Tek bir hareket olayında izin verilen en yüksek yatay mesafe (blok/tick). */
    public static double maxHorizontalSpeed(double sprintJumpBase, int speedAmp, double walkSpeedRatio,
                                            boolean onIce, double iceMultiplier, double tolerance) {
        double v = sprintJumpBase * Math.max(0.1, walkSpeedRatio);
        if (speedAmp >= 0) v *= 1.0 + 0.2 * (speedAmp + 1);
        if (onIce) v *= iceMultiplier;
        return v + tolerance;
    }

    /** İki hareket olayı arasında geçen süreye göre tolerans çarpanı (lag'da paketler birikebilir). */
    public static double tickScale(long dtMs, double maxTicks) {
        return Math.max(1.0, Math.min(maxTicks, dtMs / 50.0));
    }

    /** Ping'e göre genişletilmiş izin verilen saldırı mesafesi. */
    public static double allowedReach(double base, int pingMs, double perPingTick, double maxLeeway) {
        double leeway = Math.min(maxLeeway, (Math.max(0, pingMs) / 50.0) * perPingTick);
        return base + leeway;
    }

    /** Geri tepme beklenen yönde yeterince hareket edilmedi mi? moved: geri tepme yönündeki izdüşüm. */
    public static boolean knockbackIgnored(double velH, double moved, double ratio, double minVelocity) {
        return velH >= minVelocity && moved < velH * ratio;
    }

    /** Geri tepme değerlendirmesi için bekleme (ping gidiş-dönüş süresini kapsar). */
    public static int knockbackDelayTicks(int pingMs, int baseTicks, int maxPingTicks) {
        return baseTicks + Math.min(maxPingTicks, (int) Math.ceil(Math.max(0, pingMs) / 50.0));
    }

    /** Sadece alt sınırı veren, muhafazakâr tahmini blok kırma süresi (tick). */
    public static double minBreakTicks(double hardness, double toolSpeed, int efficiency, int hasteAmp) {
        if (hardness <= 0) return 0;
        double speed = Math.max(1.0, toolSpeed);
        if (efficiency > 0) speed += efficiency * efficiency + 1;
        if (hasteAmp >= 0) speed *= 1.0 + 0.2 * (hasteAmp + 1);
        return hardness * 30.0 / speed;
    }

    public static double stdDev(double[] v, int n) {
        if (n < 2) return Double.MAX_VALUE;
        double mean = 0;
        for (int i = 0; i < n; i++) mean += v[i];
        mean /= n;
        double s = 0;
        for (int i = 0; i < n; i++) s += (v[i] - mean) * (v[i] - mean);
        return Math.sqrt(s / n);
    }
}
