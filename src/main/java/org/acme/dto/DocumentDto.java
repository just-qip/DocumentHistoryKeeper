package org.acme.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Метаданные документа без контента.
 *
 * @param id               идентификатор
 * @param projectId        проект
 * @param title            заголовок
 * @param docKind          прикладной тип
 * @param currentVersionId id текущей версии
 * @param currentVersionAuthorId id автора текущей версии
 * @param versionSeq       номер OCC-версии сущности
 * @param createdAt        момент создания
 * @param createdBy        id создателя
 * @param updatedAt        момент последнего изменения
 * @param deletedAt        момент удаления или {@code null}
 */
public record DocumentDto(
        UUID id,
        UUID projectId,
        String title,
        String docKind,
        UUID currentVersionId,
        UUID currentVersionAuthorId,
        long versionSeq,
        Instant createdAt,
        UUID createdBy,
        Instant updatedAt,
        Instant deletedAt) {}