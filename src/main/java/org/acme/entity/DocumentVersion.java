package org.acme.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Basic;
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
 * Иммутабельный снимок документа.
 */
@Entity
@Table(schema = "docs", name = "document_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_versions_doc_number",
                columnNames = {"document_id", "version_number"}))
public class DocumentVersion extends PanacheEntityBase {

    /** Первичный ключ. */
    @Id
    public UUID id;

    /** Документ-владелец. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    public Document document;

    /** Номер версии, начиная с 1. */
    @Column(name = "version_number", nullable = false)
    public int versionNumber;

    /** Байты. Ленивая загрузка. */
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "bytea")
    public byte[] content;

    /** MIME-тип. */
    @Column(name = "mime_type", nullable = false, length = 255)
    public String mimeType;

    /** Оригинальное имя файла. */
    @Column(name = "original_name", nullable = false, length = 512)
    public String originalName;

    /** Размер в байтах. */
    @Column(name = "size_bytes", nullable = false)
    public long sizeBytes;

    /** SHA-256 содержимого. */
    @Column(name = "content_hash", nullable = false, columnDefinition = "bytea")
    public byte[] contentHash;

    /** ID версии-родителя. */
    @Column(name = "parent_version_id")
    public UUID parentVersionId;

    /** Автор версии. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    public Account author;

    /** Комментарий. */
    @Column(columnDefinition = "text")
    public String comment;

    /** Момент создания. */
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** Инициализирует ID и дату. */
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