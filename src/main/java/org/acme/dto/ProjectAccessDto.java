package org.acme.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Доступ аккаунта к проекту.
 *
 * @param id               идентификатор
 * @param projectId        проект
 * @param accountId        аккаунт
 * @param accountName      отображаемое имя аккаунта
 * @param accountEmail     email аккаунта
 * @param accountAvatarUrl публичный URL аватара или {@code null}
 * @param role             роль (VIEWER / EDITOR / OWNER)
 * @param grantedAt        момент выдачи
 * @param grantedBy        кто выдал (или {@code null})
 */
public record ProjectAccessDto(
        UUID id,
        UUID projectId,
        UUID accountId,
        String accountName,
        String accountEmail,
        String accountAvatarUrl,
        String role,
        Instant grantedAt,
        UUID grantedBy) {
}