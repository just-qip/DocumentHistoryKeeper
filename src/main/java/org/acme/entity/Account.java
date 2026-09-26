package org.acme.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.acme.enums.SystemRole;

import java.time.Instant;
import java.util.UUID;

/** Аккаунт пользователя системы. */
@Entity
@Table(schema = "docs", name = "accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_accounts_email",
                columnNames = {"email"}))
public class Account extends PanacheEntityBase {

    /** Статус жизненного цикла аккаунта. */
    public enum Status {
        ACTIVE,
        BLOCKED,
        DELETED
    }

    @Id
    public UUID id;

    @Column(nullable = false, length = 255)
    public String email;

    @Column(name = "display_name", nullable = false, length = 255)
    public String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Status status = Status.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false, length = 16)
    public SystemRole systemRole = SystemRole.USER;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** SRP6: случайная соль. */
    @Column(length = 64)
    public byte[] salt;

    /** SRP6: verifier = g^x mod N, big-endian 128 байт. */
    @Column(length = 256)
    public byte[] verifier;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = Status.ACTIVE;
    }

    /**
     * @return {@code true}, если статус {@link Status#ACTIVE}
     */
    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}