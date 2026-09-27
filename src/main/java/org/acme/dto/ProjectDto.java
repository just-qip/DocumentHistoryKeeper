package org.acme.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Проект для внешнего API.
 *
 * @param id          идентификатор
 * @param name        название
 * @param description описание
 * @param createdAt   момент создания
 * @param createdBy   id создателя
 * @param archivedAt  момент архивации или {@code null}
 * @param myRole      роль текущего пользователя в проекте:
 *                    ADMIN / OWNER / EDITOR / VIEWER или {@code null}
 */
public record ProjectDto(
        UUID id,
        String name,
        String description,
        Instant createdAt,
        UUID createdBy,
        Instant archivedAt,
        String myRole) {
}