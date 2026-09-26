package org.acme.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Доступ аккаунта к проекту.
 *
 * @param id        идентификатор записи
 * @param projectId проект
 * @param accountId аккаунт
 * @param role      роль (VIEWER / EDITOR / OWNER)
 * @param grantedAt момент выдачи
 * @param grantedBy кто выдал (или {@code null})
 */
public record ProjectAccessDto(
        UUID id,
        UUID projectId,
        UUID accountId,
        String role,
        Instant grantedAt,
        UUID grantedBy) {
}