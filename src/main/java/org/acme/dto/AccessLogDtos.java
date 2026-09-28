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
     * @param id           идентификатор записи
     * @param at           момент
     * @param action       VIEW / PREVIEW / DOWNLOAD
     * @param versionId    версия (для VIEW = null)
     * @param versionNumber номер версии (для VIEW = null)
     * @param accountId    аккаунт
     * @param accountName  имя аккаунта
     * @param accountEmail email
     * @param ipAddress    IP
     * @param deviceType   desktop / mobile / tablet / bot / unknown
     * @param osName       ОС
     * @param browserName  браузер
     * @param userAgent    сырой User-Agent
     */
    public record AccessLogEntryDto(
            Long id,
            Instant at,
            String action,
            UUID versionId,
            Integer versionNumber,
            UUID accountId,
            String accountName,
            String accountEmail,
            String ipAddress,
            String deviceType,
            String osName,
            String browserName,
            String userAgent) {
    }

    /**
     * @param documentId документ
     * @param entries    записи страницы
     * @param nextCursor момент последней записи или null
     */
    public record AccessLogPageDto(
            UUID documentId,
            List<AccessLogEntryDto> entries,
            Instant nextCursor) {
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
     * @param totalViews     всего VIEW
     * @param totalPreviews  всего PREVIEW
     * @param totalDownloads всего DOWNLOAD
     * @param uniqueViewers  число уникальных аккаунтов
     * @param perVersion     статистика по версиям
     * @param perUser        статистика по пользователям
     */
    public record AccessLogStatsDto(
            UUID documentId,
            long totalViews,
            long totalPreviews,
            long totalDownloads,
            long uniqueViewers,
            List<PerVersionStatDto> perVersion,
            List<PerUserStatDto> perUser) {
    }
}