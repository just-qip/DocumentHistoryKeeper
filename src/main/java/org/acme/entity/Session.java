package org.acme.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Сессия аккаунта. Хранит SHA-256 от токена. */
@Entity
@Table(schema = "docs", name = "sessions")
public class Session extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "account_id", nullable = false)
    public UUID accountId;

    @Column(name = "token_hash", nullable = false, length = 32)
    public byte[] tokenHash;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "last_seen_at", nullable = false)
    public Instant lastSeenAt;
}