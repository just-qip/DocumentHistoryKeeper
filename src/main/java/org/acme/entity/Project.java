package org.acme.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Проект — контейнер верхнего уровня.
 */
@Entity
@Table(schema = "docs", name = "projects",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_projects_tenant_name",
                columnNames = {"tenant_id", "name"}))
public class Project extends PanacheEntityBase {

    /** Первичный ключ. */
    @Id
    public UUID id;

    /** Идентификатор тенанта. */
    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    /** Название проекта. */
    @Column(nullable = false, length = 255)
    public String name;

    /** Описание. */
    @Column(columnDefinition = "text")
    public String description;

    /** Момент создания. */
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** Создатель. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    public Account createdBy;

    /** Момент архивации. */
    @Column(name = "archived_at")
    public Instant archivedAt;

    /** Инициализирует ID и дату создания. */
    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}