package org.acme.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.acme.enums.ProjectRole;

import java.time.Instant;
import java.util.UUID;

/** Связь «аккаунт ↔ проект» с ролью. */
@Entity
@Table(schema = "docs", name = "project_access",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_project_access_project_account",
                columnNames = {"project_id", "account_id"}))
public class ProjectAccess extends PanacheEntityBase {

    @Id
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    public Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    public Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public ProjectRole role;

    @Column(name = "granted_at", nullable = false)
    public Instant grantedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    public Account grantedBy;
}