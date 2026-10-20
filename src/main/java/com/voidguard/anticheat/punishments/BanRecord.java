package com.voidguard.anticheat.punishments;

import java.util.UUID;

/** Ban / kick kaydı. */
public final class BanRecord {
    public volatile long id;
    public final UUID uuid;
    public final String name;
    public final String reason;
    public final String checkName;
    public final String evidence;
    public final String type; // BAN, TEMPBAN, KICK
    public final String issuedBy;
    public final long bannedAt;
    public final long expiresAt; // 0 = kalıcı
    public volatile boolean active;

    public BanRecord(long id, UUID uuid, String name, String reason, String checkName, String evidence,
                     String type, String issuedBy, long bannedAt, long expiresAt, boolean active) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
        this.reason = reason;
        this.checkName = checkName;
        this.evidence = evidence;
        this.type = type;
        this.issuedBy = issuedBy;
        this.bannedAt = bannedAt;
        this.expiresAt = expiresAt;
        this.active = active;
    }

    public boolean expired(long now) {
        return expiresAt > 0 && now >= expiresAt;
    }
}
