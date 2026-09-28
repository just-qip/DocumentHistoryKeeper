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
import org.acme.enums.AccessAction;

import java.time.Instant;
import java.util.UUID;

/** Запись аудита: чтение, скачивание или отказанная попытка. Append-only. */
@Entity
@Table(schema = "docs", name = "document_access_log")
public class DocumentAccessLog extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "document_id", nullable = false)
    public UUID documentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", insertable = false, updatable = false)
    public Document document;

    @Column(name = "version_id")
    public UUID versionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", insertable = false, updatable = false)
    public DocumentVersion version;

    /** Номер версии, к которой пытались обратиться, но не имели доступа. */
    @Column(name = "attempted_version_number")
    public Integer attemptedVersionNumber;

    /** Аккаунт или null для анонимных попыток. */
    @Column(name = "account_id")
    public UUID accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    public Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public AccessAction action;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(name = "ip_address", length = 45)
    public String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    public String userAgent;

    @Column(name = "device_type", length = 16)
    public String deviceType;

    @Column(name = "os_name", length = 64)
    public String osName;

    @Column(name = "browser_name", length = 64)
    public String browserName;

    /** NULL = успешный доступ; иначе — причина отказа. */
    @Column(name = "denied_reason", length = 32)
    public String deniedReason;

    @PrePersist
    void prePersist() {
        if (occurredAt == null) occurredAt = Instant.now();
    }
}