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

import java.time.Instant;
import java.util.UUID;

/**
 * Аккаунт пользователя системы.
 */
@Entity
@Table(schema = "docs", name = "accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_accounts_tenant_email",
                columnNames = {"tenant_id", "email"}))
public class Account extends PanacheEntityBase {

    /**
     * Статус жизненного цикла аккаунта.
     */
    public enum Status {
        ACTIVE,
        BLOCKED,
        DELETED
    }

    /** Первичный ключ. */
    @Id
    public UUID id;

    /** Идентификатор тенанта. */
    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    /** Email, уникальный в пределах тенанта. */
    @Column(nullable = false, length = 255)
    public String email;

    /** Отображаемое имя. */
    @Column(name = "display_name", nullable = false, length = 255)
    public String displayName;

    /** Текущий статус. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Status status = Status.ACTIVE;

    /** Момент создания записи. */
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** Инициализирует ID, дату и статус перед первой вставкой. */
    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = Status.ACTIVE;
        }
    }

    /**
     * Проверяет, что аккаунт активен.
     *
     * @return {@code true}, если статус {@link Status#ACTIVE}
     */
    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}