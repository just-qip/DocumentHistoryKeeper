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
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Логический документ с постоянным идентификатором.
 */
@Entity
@Table(schema = "docs", name = "documents")
public class Document extends PanacheEntityBase {

    /** Первичный ключ. */
    @Id
    public UUID id;

    /** Проект-владелец. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    public Project project;

    /** Заголовок. */
    @Column(nullable = false, length = 512)
    public String title;

    /** Прикладной тип документа. */
    @Column(name = "doc_kind", nullable = false, length = 64)
    public String docKind;

    /** Идентификатор текущей версии. */
    @Column(name = "current_version_id")
    public UUID currentVersionId;

    /** Автор текущей версии (денормализация для быстрых списков). */
    @Column(name = "current_version_author_id")
    public UUID currentVersionAuthorId;

    /** Optimistic lock. */
    @Version
    @Column(name = "version_seq", nullable = false)
    public long versionSeq;

    /** Момент создания. */
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** Создатель. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    public Account createdBy;

    /** Момент последнего изменения. */
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    /** Момент soft delete. */
    @Column(name = "deleted_at")
    public Instant deletedAt;

    /** Инициализирует ID и таймстемпы. */
    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
}