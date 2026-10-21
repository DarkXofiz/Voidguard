package com.voidguard.anticheat.core;

/**
 * Bir hareket paketinin Bukkit'ten bağımsız özeti. Çevresel durumlar (sıvı, sarmaşık, buz vb.)
 * adapter katmanında hesaplanıp bayrak olarak gelir; kontroller yalnızca matematikle ilgilenir.
 *
 * @param clientGround istemcinin bildirdiği onGround (yalnızca NoFall karşılaştırması için; güvenilmez)
 * @param standing     SUNUCUNUN çarpışma sorgusuna göre oyuncu bir bloğun üstünde duruyor mu (güvenilir)
 * @param exempt       sert muafiyet (creative, uçuş, elytra, araç, teleport/giriş toleransı, lag...)
 * @param velocity     sunucu az önce hız verdi (knockback, patlama, rüzgar topu...)
 * @param special      sıvı, tırmanılabilir blok, ağ, bal/slime, piston vb. özel zemin/ortam
 * @param gravityOff   levitation / yavaş düşme gibi yerçekimini değiştiren etki
 * @param ice          buz benzeri kaygan zemin (yakın geçmiş dahil)
 * @param speedMul     hız çarpanı (efekt + yürüme hızı)
 * @param jumpLevel    Zıplama Güçlendirme seviyesi (0 = yok)
 * @param usingItem    yemek/yay/kalkan kullanıyor
 * @param serverFall   sunucunun tuttuğu fallDistance
 */
public final class MoveSample {
    private final long tick;
    private final double dx;
    private final double dy;
    private final double dz;
    private final float yaw;
    private final float pitch;
    private final boolean clientGround;
    private final boolean standing;
    private final boolean exempt;
    private final boolean velocity;
    private final boolean special;
    private final boolean gravityOff;
    private final boolean ice;
    private final double speedMul;
    private final int jumpLevel;
    private final boolean usingItem;
    private final double serverFall;
    public MoveSample(long tick, double dx, double dy, double dz, float yaw, float pitch, boolean clientGround, boolean standing, boolean exempt, boolean velocity, boolean special, boolean gravityOff, boolean ice, double speedMul, int jumpLevel, boolean usingItem, double serverFall) {
        this.tick = tick;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.yaw = yaw;
        this.pitch = pitch;
        this.clientGround = clientGround;
        this.standing = standing;
        this.exempt = exempt;
        this.velocity = velocity;
        this.special = special;
        this.gravityOff = gravityOff;
        this.ice = ice;
        this.speedMul = speedMul;
        this.jumpLevel = jumpLevel;
        this.usingItem = usingItem;
        this.serverFall = serverFall;
    }
    public long tick() { return tick; }
    public double dx() { return dx; }
    public double dy() { return dy; }
    public double dz() { return dz; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public boolean clientGround() { return clientGround; }
    public boolean standing() { return standing; }
    public boolean exempt() { return exempt; }
    public boolean velocity() { return velocity; }
    public boolean special() { return special; }
    public boolean gravityOff() { return gravityOff; }
    public boolean ice() { return ice; }
    public double speedMul() { return speedMul; }
    public int jumpLevel() { return jumpLevel; }
    public boolean usingItem() { return usingItem; }
    public double serverFall() { return serverFall; }
    public double hSpeed() { return Math.hypot(dx, dz); }
}
