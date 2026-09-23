package org.acme.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Метаданные версии без контента. Байты отдаются отдельным эндпоинтом
 * {@code GET /documents/{id}/versions/{n}/content}.
 *
 * @param id              идентификатор
 * @param documentId      документ
 * @param versionNumber   номер версии
 * @param mimeType        MIME-тип
 * @param originalName    исходное имя файла
 * @param sizeBytes       размер
 * @param sha256          SHA-256 в hex
 * @param parentVersionId id родительской версии
 * @param authorId        id автора
 * @param comment         комментарий
 * @param createdAt       момент создания
 */
public record VersionMetaDto(
        UUID id,
        UUID documentId,
        int versionNumber,
        String mimeType,
        String originalName,
        long sizeBytes,
        String sha256,
        UUID parentVersionId,
        UUID authorId,
        String comment,
        Instant createdAt) {
}