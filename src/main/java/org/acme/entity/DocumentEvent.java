package org.acme.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.acme.enums.EventType;

import java.time.Instant;
import java.util.UUID;

/**
 * Запись хронологии документа. Append-only.
 *
 * <p>Payload хранится как {@code TEXT} с JSON-строкой намеренно:
 * Postgres нормализует {@code JSONB} и ломает побайтовое сравнение
 * при верификации хеш-цепочки. Плюс Hibernate не подключает JSON-FormatMapper,
 * а значит, нет конфликта с REST-ObjectMapper.</p>
 */
@Entity
@Table(schema = "docs", name = "document_events")
public class DocumentEvent extends PanacheEntityBase {

    /** Первичный ключ. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Документ. */
    @Column(name = "document_id", nullable = false)
    public UUID documentId;

    /** Проект — денормализация. */
    @Column(name = "project_id", nullable = false)
    public UUID projectId;

    /** Версия, если применимо. */
    @Column(name = "version_id")
    public UUID versionId;

    /** Тип события. */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    public EventType eventType;

    /** Актор. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    public Account actor;

    /** Момент события. */
    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    /** JSON payload стабильной строкой. */
    @Column(name = "payload", nullable = false, columnDefinition = "text")
    public String payload;

    /** Хеш предыдущего события. */
    @Column(name = "prev_event_hash", columnDefinition = "bytea")
    public byte[] prevEventHash;

    /** Хеш текущего события. */
    @Column(name = "event_hash", nullable = false, columnDefinition = "bytea")
    public byte[] eventHash;

    /** Проставляет время события. */
    @PrePersist
    void prePersist() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}