package org.acme.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO для аудита доступа к документу.
 */
public final class AccessLogDtos {

    private AccessLogDtos() {
    }

    /**
     * @param id                     идентификатор записи
     * @param at                     момент
     * @param action                 VIEW / PREVIEW / DOWNLOAD
     * @param versionId              версия (для успешного доступа)
     * @param versionNumber          номер версии (для успешного доступа)
     * @param attemptedVersionNumber номер версии из URL, когда доступа не было
     * @param accountId              аккаунт или null (анонимная попытка)
     * @param accountName            имя или null
     * @param accountEmail           email или null
     * @param ipAddress              IP
     * @param deviceType             desktop / mobile / tablet / bot / unknown
     * @param osName                 ОС
     * @param browserName            браузер
     * @param userAgent              сырой User-Agent
     * @param deniedReason           причина отказа или null для успеха
     */
    public record AccessLogEntryDto(
            Long id,
            Instant at,
            String action,
            UUID versionId,
            Integer versionNumber,
            Integer attemptedVersionNumber,
            UUID accountId,
            String accountName,
            String accountEmail,
            String ipAddress,
            String deviceType,
            String osName,
            String browserName,
            String userAgent,
            String deniedReason) {
    }

    /**
     * Страница журнала аудита.
     *
     * @param documentId    документ
     * @param entries       записи страницы
     * @param page          номер страницы (0-based)
     * @param size          размер страницы
     * @param totalElements всего записей под текущим фильтром
     * @param totalPages    всего страниц
     */
    public record AccessLogPageDto(
            UUID documentId,
            List<AccessLogEntryDto> entries,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    /**
     * @param versionId     версия
     * @param versionNumber номер версии
     * @param views         количество VIEW
     * @param previews      количество PREVIEW
     * @param downloads     количество DOWNLOAD
     * @param lastAccessAt  последний доступ
     */
    public record PerVersionStatDto(
            UUID versionId,
            Integer versionNumber,
            long views,
            long previews,
            long downloads,
            Instant lastAccessAt) {
    }

    /**
     * @param accountId    аккаунт
     * @param accountName  имя
     * @param views        количество VIEW
     * @param previews     количество PREVIEW
     * @param downloads    количество DOWNLOAD
     * @param lastAccessAt последний доступ
     */
    public record PerUserStatDto(
            UUID accountId,
            String accountName,
            long views,
            long previews,
            long downloads,
            Instant lastAccessAt) {
    }

    /**
     * @param documentId     документ
     * @param totalViews     всего успешных VIEW
     * @param totalPreviews  всего успешных PREVIEW
     * @param totalDownloads всего успешных DOWNLOAD
     * @param totalDenied    всего отказанных попыток
     * @param uniqueViewers  число уникальных аккаунтов с успешным доступом
     * @param perVersion     статистика по версиям (успешные)
     * @param perUser        статистика по пользователям (успешные)
     */
    public record AccessLogStatsDto(
            UUID documentId,
            long totalViews,
            long totalPreviews,
            long totalDownloads,
            long totalDenied,
            long uniqueViewers,
            List<PerVersionStatDto> perVersion,
            List<PerUserStatDto> perUser) {
    }
}